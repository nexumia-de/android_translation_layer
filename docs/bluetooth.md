---
type: Concept
title: Bluetooth on BlueZ
description: How ATL maps android.bluetooth (adapter, devices, bonding, discovery, LE scanning, GATT client) onto BlueZ over the D-Bus system bus, what is left out, and how to test it.
tags: [bluetooth, ble, gatt, bluez, dbus, gadgetbridge]
resource: ../src/api-impl/android/bluetooth/BlueZ.java
generated: { by: claude-code/opus-5.5, at: 2026-11-02T09:00:00Z }
sources:
  - id: bluez-device
    resource: https://git.kernel.org/pub/scm/bluetooth/bluez.git/tree/doc/org.bluez.Device.rst
    title: BlueZ, org.bluez.Device1
  - id: bluez-gatt
    resource: https://git.kernel.org/pub/scm/bluetooth/bluez.git/tree/doc/org.bluez.GattCharacteristic.rst
    title: BlueZ, org.bluez.GattCharacteristic1 (ReadValue, WriteValue, StartNotify, MTU)
  - id: bluez-adapter
    resource: https://git.kernel.org/pub/scm/bluetooth/bluez.git/tree/doc/org.bluez.Adapter.rst
    title: BlueZ, org.bluez.Adapter1 (StartDiscovery, SetDiscoveryFilter)
  - id: android-gatt
    resource: https://developer.android.com/reference/android/bluetooth/BluetoothGatt
    title: Android developers, BluetoothGatt
---

# Design

Until 2026-11-02 ATL had no Bluetooth: `BluetoothAdapter.getDefaultAdapter()`
returned null. Now `android.bluetooth` talks to BlueZ on the system bus, which
every user may use on the phone (no group or polkit rule was needed for the
user `claude`).

- `BlueZ.java` keeps a copy of BlueZ's object tree (`GetManagedObjects`), kept
  current from `PropertiesChanged`, `InterfacesAdded` and `InterfacesRemoved`,
  and turns adapter and device changes into Android's broadcasts
  (`STATE_CHANGED`, `BOND_STATE_CHANGED`, `ACL_CONNECTED`/`DISCONNECTED`,
  `NAME_CHANGED`, `UUID`, `FOUND`), delivered on the main thread.
- The native bridge (`src/api-impl-jni/bluetooth/android_bluetooth_BlueZ.c`)
  has one generic synchronous call with GVariant to Java conversion, and a
  thread with its own main context for BlueZ's signals.
- The default adapter is BlueZ's first `Adapter1`; a device is the `Device1`
  object of its address. `getType()` is guessed (random address or LE
  advertising data means LE, a class of device means classic).
- Discovery: one BlueZ discovery session for the process. LE scans set the
  transport filter `le`, classic discovery `auto` and ends after 12 s like
  Android. Scan filters are applied in ATL. `ScanRecord.getBytes()` is
  rebuilt from BlueZ's parsed fields, as BlueZ does not hand out the raw
  advertisement[^bluez-adapter].
- Bonding: `createBond()` calls `Device1.Pair`. While it pairs or connects,
  ATL registers its own `org.bluez.Agent1` (`PairingAgent`): a request goes
  to the app as the ordered broadcast `ACTION_PAIRING_REQUEST` (answered with
  `setPairingConfirmation`/`setPin`), else ATL shows an Android-like pairing
  dialog. No desktop agent (GNOME Settings' Bluetooth panel) is needed.
- Discovery is paused during `Connect` and `Pair`: with discovery running,
  BlueZ aborts LE connections (`le-connection-abort-by-local`, reproduced
  with bluetoothctl on the phone). `StopDiscovery` is retried on `InProgress`.
- GATT client[^android-gatt][^bluez-gatt]: `connectGatt` calls
  `Device1.Connect` (or uses the existing link), `discoverServices` waits for
  `ServicesResolved` and builds the service tree from the `Gatt*1` objects,
  sorted by handle. Reads and writes are `ReadValue`/`WriteValue` (`type`
  `command` for write without response) on an own thread per connection;
  callbacks run on the given `Handler` or a second own thread.
- Notifications: BlueZ writes the Client Characteristic Configuration
  descriptor itself in `StartNotify` and refuses direct writes to it. So
  `setCharacteristicNotification(true)` starts BlueZ's notification session,
  and a write of the CCCD only reports success (or starts the session if the
  app skipped the first step). A notification is a change of the
  characteristic's `Value`; the echo of a `ReadValue` is filtered out.
- `requestMtu` reports the MTU BlueZ negotiated on connect (`MTU` property).
- `disconnect()` calls `Device1.Disconnect`, which takes down the link for all
  users of the device, unlike Android, where other apps keep it up.

# Not implemented

| What | Effect | Size |
|---|---|---|
| GATT server (`openGattServer`) | returns null; Gadgetbridge devices that need a server on the phone fail to connect | medium: BlueZ `GattManager1` with exported objects, about 2 days |
| RFCOMM sockets | `connect()` throws; classic devices (Pebble and other older watches) cannot connect | medium: a BlueZ `Profile1` object that receives the socket, about 1 day |
| Profile proxies (A2DP, headset) | `getProfileProxy` returns false | out of scope, the desktop's audio stack owns them |
| Bluetooth inside an app's own Flatpak | the sandbox has no system bus, so BlueZ is unreachable: a sandboxed Gadgetbridge finds no adapter. The feature `android.hardware.bluetooth` is still reported (from `/sys/class/bluetooth`), so stores in a Flatpak list Bluetooth apps as compatible (2026-11-04) | small-medium: `--system-talk-name=org.bluez` as user override after the app's own permission dialog ([roadmap](hardening-roadmap.md#mapping)), plus a check that the pairing agent works through the proxy |
| Bearer choice | `TRANSPORT_LE` on dual-mode devices is not passed to BlueZ | small, needs BlueZ 5.80 `Bearer` interfaces |

# Testing

Headless tests reach the real adapter. Scanning is harmless; connecting to
someone's device is not, so connections and GATT traffic are owner tests.
`busctl --system tree org.bluez` shows BlueZ's state, and
`busctl --system call org.bluez <char path> org.bluez.GattCharacteristic1
ReadValue 'a{sv}' 0` checks access to a characteristic.
