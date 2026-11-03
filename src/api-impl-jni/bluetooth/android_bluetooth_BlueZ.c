/*
 * Bridge from android.bluetooth to BlueZ on the D-Bus system bus.
 *
 * The Java side (android.bluetooth.BlueZ) calls BlueZ methods synchronously
 * from its own worker threads with nativeCall, and receives BlueZ's signals
 * (PropertiesChanged, InterfacesAdded, InterfacesRemoved) through onSignal on
 * a dedicated thread with its own GMainContext, so a busy UI thread does not
 * delay GATT notifications.
 *
 * Values are converted between GVariant and Java objects as follows:
 *   b <-> Boolean, y/n/q/i/u <-> Integer (q also from Character), x/t <-> Long,
 *   d <-> Double, s/o/g <-> String, ay <-> byte[], as/ao <-> String[],
 *   a{..} <-> HashMap, tuples -> Object[], v -> the contained value.
 */

#include <gio/gio.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_bluetooth_BlueZ.h"
#include "../generated_headers/android_bluetooth_PairingAgent.h"

static GDBusConnection *system_bus = NULL;
static GMainContext *signal_context = NULL;

static jobject variant_to_java(JNIEnv *env, GVariant *value);

static jobject box(JNIEnv *env, const char *class_name, const char *signature, ...)
{
	va_list args;
	va_start(args, signature);
	jclass class = (*env)->FindClass(env, class_name);
	jmethodID value_of = _STATIC_METHOD(class, "valueOf", signature);
	jobject result = (*env)->CallStaticObjectMethodV(env, class, value_of, args);
	(*env)->DeleteLocalRef(env, class);
	va_end(args);
	return result;
}

static jobject variant_to_java(JNIEnv *env, GVariant *value)
{
	const GVariantType *type = g_variant_get_type(value);

	if (g_variant_type_is_variant(type)) {
		GVariant *inner = g_variant_get_variant(value);
		jobject result = variant_to_java(env, inner);
		g_variant_unref(inner);
		return result;
	}
	if (g_variant_type_equal(type, G_VARIANT_TYPE_BOOLEAN))
		return box(env, "java/lang/Boolean", "(Z)Ljava/lang/Boolean;", (jboolean)g_variant_get_boolean(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_BYTE))
		return box(env, "java/lang/Integer", "(I)Ljava/lang/Integer;", (jint)g_variant_get_byte(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_INT16))
		return box(env, "java/lang/Integer", "(I)Ljava/lang/Integer;", (jint)g_variant_get_int16(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_UINT16))
		return box(env, "java/lang/Integer", "(I)Ljava/lang/Integer;", (jint)g_variant_get_uint16(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_INT32))
		return box(env, "java/lang/Integer", "(I)Ljava/lang/Integer;", (jint)g_variant_get_int32(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_UINT32))
		return box(env, "java/lang/Integer", "(I)Ljava/lang/Integer;", (jint)g_variant_get_uint32(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_HANDLE))
		return box(env, "java/lang/Integer", "(I)Ljava/lang/Integer;", (jint)g_variant_get_handle(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_INT64))
		return box(env, "java/lang/Long", "(J)Ljava/lang/Long;", (jlong)g_variant_get_int64(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_UINT64))
		return box(env, "java/lang/Long", "(J)Ljava/lang/Long;", (jlong)g_variant_get_uint64(value));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_DOUBLE))
		return box(env, "java/lang/Double", "(D)Ljava/lang/Double;", (jdouble)g_variant_get_double(value));
	if (g_variant_type_is_basic(type)) /* s, o, g */
		return _JSTRING(g_variant_get_string(value, NULL));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_BYTESTRING)) {
		gsize length;
		const guint8 *data = g_variant_get_fixed_array(value, &length, 1);
		jbyteArray array = (*env)->NewByteArray(env, length);
		(*env)->SetByteArrayRegion(env, array, 0, length, (const jbyte *)data);
		return array;
	}
	if (g_variant_type_equal(type, G_VARIANT_TYPE_STRING_ARRAY) || g_variant_type_equal(type, G_VARIANT_TYPE_OBJECT_PATH_ARRAY)) {
		gsize n = g_variant_n_children(value);
		jclass string_class = (*env)->FindClass(env, "java/lang/String");
		jobjectArray array = (*env)->NewObjectArray(env, n, string_class, NULL);
		for (gsize i = 0; i < n; i++) {
			GVariant *child = g_variant_get_child_value(value, i);
			jstring string = _JSTRING(g_variant_get_string(child, NULL));
			(*env)->SetObjectArrayElement(env, array, i, string);
			(*env)->DeleteLocalRef(env, string);
			g_variant_unref(child);
		}
		(*env)->DeleteLocalRef(env, string_class);
		return array;
	}
	if (g_variant_type_is_array(type) && g_variant_type_is_dict_entry(g_variant_type_element(type))) {
		jclass map_class = (*env)->FindClass(env, "java/util/HashMap");
		jobject map = (*env)->NewObject(env, map_class, _METHOD(map_class, "<init>", "()V"));
		jmethodID put = _METHOD(map_class, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
		gsize n = g_variant_n_children(value);
		for (gsize i = 0; i < n; i++) {
			GVariant *entry = g_variant_get_child_value(value, i);
			GVariant *key = g_variant_get_child_value(entry, 0);
			GVariant *val = g_variant_get_child_value(entry, 1);
			jobject jkey = variant_to_java(env, key);
			jobject jval = variant_to_java(env, val);
			jobject old = (*env)->CallObjectMethod(env, map, put, jkey, jval);
			if (old)
				(*env)->DeleteLocalRef(env, old);
			(*env)->DeleteLocalRef(env, jkey);
			if (jval)
				(*env)->DeleteLocalRef(env, jval);
			g_variant_unref(key);
			g_variant_unref(val);
			g_variant_unref(entry);
		}
		(*env)->DeleteLocalRef(env, map_class);
		return map;
	}
	if (g_variant_type_is_array(type) || g_variant_type_is_tuple(type)) {
		gsize n = g_variant_n_children(value);
		jclass object_class = (*env)->FindClass(env, "java/lang/Object");
		jobjectArray array = (*env)->NewObjectArray(env, n, object_class, NULL);
		for (gsize i = 0; i < n; i++) {
			GVariant *child = g_variant_get_child_value(value, i);
			jobject jchild = variant_to_java(env, child);
			(*env)->SetObjectArrayElement(env, array, i, jchild);
			if (jchild)
				(*env)->DeleteLocalRef(env, jchild);
			g_variant_unref(child);
		}
		(*env)->DeleteLocalRef(env, object_class);
		return array;
	}
	return NULL;
}

static gboolean is_instance(JNIEnv *env, jobject object, const char *class_name)
{
	jclass class = (*env)->FindClass(env, class_name);
	gboolean result = (*env)->IsInstanceOf(env, object, class);
	(*env)->DeleteLocalRef(env, class);
	return result;
}

static jlong number_value(JNIEnv *env, jobject object)
{
	jclass class = _CLASS(object);
	jlong result;
	if (is_instance(env, object, "java/lang/Character"))
		result = (*env)->CallCharMethod(env, object, _METHOD(class, "charValue", "()C"));
	else if (is_instance(env, object, "java/lang/Boolean"))
		result = (*env)->CallBooleanMethod(env, object, _METHOD(class, "booleanValue", "()Z"));
	else
		result = (*env)->CallLongMethod(env, object, _METHOD(class, "longValue", "()J"));
	(*env)->DeleteLocalRef(env, class);
	return result;
}

static GVariant *java_to_variant(JNIEnv *env, jobject object, const GVariantType *type);

/* the type a value inside a "v" gets: Character is the only way to say uint16, which BlueZ wants for "offset" and "mtu" */
static GVariant *java_to_guessed_variant(JNIEnv *env, jobject object)
{
	const char *type;
	if (is_instance(env, object, "java/lang/Boolean"))
		type = "b";
	else if (is_instance(env, object, "java/lang/Character"))
		type = "q";
	else if (is_instance(env, object, "java/lang/Short"))
		type = "n";
	else if (is_instance(env, object, "java/lang/Integer"))
		type = "i";
	else if (is_instance(env, object, "java/lang/Long"))
		type = "x";
	else if (is_instance(env, object, "java/lang/String"))
		type = "s";
	else if (is_instance(env, object, "[B"))
		type = "ay";
	else if (is_instance(env, object, "[Ljava/lang/String;"))
		type = "as";
	else
		return NULL;
	return java_to_variant(env, object, G_VARIANT_TYPE(type));
}

static GVariant *java_to_variant(JNIEnv *env, jobject object, const GVariantType *type)
{
	if (!object)
		return NULL;
	if (g_variant_type_is_variant(type)) {
		GVariant *inner = java_to_guessed_variant(env, object);
		return inner ? g_variant_new_variant(inner) : NULL;
	}
	if (g_variant_type_equal(type, G_VARIANT_TYPE_BOOLEAN))
		return g_variant_new_boolean(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_BYTE))
		return g_variant_new_byte(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_INT16))
		return g_variant_new_int16(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_UINT16))
		return g_variant_new_uint16(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_INT32))
		return g_variant_new_int32(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_UINT32))
		return g_variant_new_uint32(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_INT64))
		return g_variant_new_int64(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_UINT64))
		return g_variant_new_uint64(number_value(env, object));
	if (g_variant_type_equal(type, G_VARIANT_TYPE_STRING) || g_variant_type_equal(type, G_VARIANT_TYPE_OBJECT_PATH)) {
		const char *string = _CSTRING(object);
		GVariant *result = g_variant_type_equal(type, G_VARIANT_TYPE_STRING) ? g_variant_new_string(string) : g_variant_new_object_path(string);
		(*env)->ReleaseStringUTFChars(env, object, string);
		return result;
	}
	if (g_variant_type_equal(type, G_VARIANT_TYPE_BYTESTRING)) {
		jsize length = (*env)->GetArrayLength(env, object);
		jbyte *data = (*env)->GetByteArrayElements(env, object, NULL);
		GVariant *result = g_variant_new_fixed_array(G_VARIANT_TYPE_BYTE, data, length, 1);
		(*env)->ReleaseByteArrayElements(env, object, data, JNI_ABORT);
		return result;
	}
	if (g_variant_type_is_array(type) && g_variant_type_is_dict_entry(g_variant_type_element(type))) {
		/* Map: iterate over entrySet() */
		const GVariantType *entry_type = g_variant_type_element(type);
		const GVariantType *key_type = g_variant_type_key(entry_type);
		const GVariantType *value_type = g_variant_type_value(entry_type);
		GVariantBuilder builder;
		g_variant_builder_init(&builder, type);
		if (object) {
			jobject entries = (*env)->CallObjectMethod(env, object, _METHOD(_CLASS(object), "entrySet", "()Ljava/util/Set;"));
			jobjectArray array = (*env)->CallObjectMethod(env, entries, _METHOD(_CLASS(entries), "toArray", "()[Ljava/lang/Object;"));
			jsize n = (*env)->GetArrayLength(env, array);
			for (jsize i = 0; i < n; i++) {
				jobject entry = (*env)->GetObjectArrayElement(env, array, i);
				jclass entry_class = (*env)->FindClass(env, "java/util/Map$Entry");
				jobject key = (*env)->CallObjectMethod(env, entry, _METHOD(entry_class, "getKey", "()Ljava/lang/Object;"));
				jobject val = (*env)->CallObjectMethod(env, entry, _METHOD(entry_class, "getValue", "()Ljava/lang/Object;"));
				GVariant *gkey = java_to_variant(env, key, key_type);
				GVariant *gval = java_to_variant(env, val, value_type);
				if (gkey && gval)
					g_variant_builder_add_value(&builder, g_variant_new_dict_entry(gkey, gval));
				(*env)->DeleteLocalRef(env, entry_class);
				(*env)->DeleteLocalRef(env, entry);
				(*env)->DeleteLocalRef(env, key);
				(*env)->DeleteLocalRef(env, val);
			}
		}
		return g_variant_builder_end(&builder);
	}
	if (g_variant_type_is_array(type)) {
		/* Object[] or String[] */
		const GVariantType *element_type = g_variant_type_element(type);
		GVariantBuilder builder;
		g_variant_builder_init(&builder, type);
		jsize n = object ? (*env)->GetArrayLength(env, object) : 0;
		for (jsize i = 0; i < n; i++) {
			jobject element = (*env)->GetObjectArrayElement(env, object, i);
			GVariant *gelement = java_to_variant(env, element, element_type);
			if (gelement)
				g_variant_builder_add_value(&builder, gelement);
			(*env)->DeleteLocalRef(env, element);
		}
		return g_variant_builder_end(&builder);
	}
	return NULL;
}

static void throw_io_exception(JNIEnv *env, GError *error)
{
	char *remote = g_dbus_error_get_remote_error(error);
	if (remote)
		g_dbus_error_strip_remote_error(error);
	char *message = g_strdup_printf("%s: %s", remote ? remote : "org.freedesktop.DBus.Error.Failed", error->message);
	(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), message);
	g_free(message);
	g_free(remote);
}

static void on_signal(GDBusConnection *connection, const char *sender, const char *path, const char *interface,
                      const char *member, GVariant *parameters, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	jstring jpath = _JSTRING(path);
	jstring jinterface = _JSTRING(interface);
	jstring jmember = _JSTRING(member);
	jobject jparameters = variant_to_java(env, parameters);
	J__BlueZ__onSignal(env, jpath, jinterface, jmember, jparameters);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	(*env)->DeleteLocalRef(env, jpath);
	(*env)->DeleteLocalRef(env, jinterface);
	(*env)->DeleteLocalRef(env, jmember);
	if (jparameters)
		(*env)->DeleteLocalRef(env, jparameters);
}

static void export_agent(void);

static gpointer signal_thread(gpointer data)
{
	JavaVM *vm = data;
	JNIEnv *env;
	JavaVMAttachArgs args = {.version = JNI_VERSION_1_6, .name = "BlueZ signals", .group = NULL};
	(*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&env, &args);

	g_main_context_push_thread_default(signal_context);
	/* sender "org.bluez" also matches by name owner, so a restarted bluetoothd is still heard */
	g_dbus_connection_signal_subscribe(system_bus, "org.bluez", "org.freedesktop.DBus.Properties", "PropertiesChanged",
	                                   NULL, NULL, G_DBUS_SIGNAL_FLAGS_NONE, on_signal, NULL, NULL);
	g_dbus_connection_signal_subscribe(system_bus, "org.bluez", "org.freedesktop.DBus.ObjectManager", NULL,
	                                   NULL, NULL, G_DBUS_SIGNAL_FLAGS_NONE, on_signal, NULL, NULL);
	/* exported here, so BlueZ's calls are dispatched on this thread's context */
	export_agent();
	GMainLoop *loop = g_main_loop_new(signal_context, FALSE);
	g_main_loop_run(loop);
	return NULL;
}

JNIEXPORT jboolean JNICALL Java_android_bluetooth_BlueZ_nativeInit(JNIEnv *env, jclass class)
{
	if (system_bus)
		return JNI_TRUE;
	GError *error = NULL;
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, &error);
	if (!bus) {
		fprintf(stderr, "BlueZ: no system bus: %s\n", error->message);
		g_error_free(error);
		return JNI_FALSE;
	}
	system_bus = bus;
	signal_context = g_main_context_new();
	JavaVM *vm;
	(*env)->GetJavaVM(env, &vm);
	g_thread_unref(g_thread_new("BlueZ signals", signal_thread, vm));
	return JNI_TRUE;
}

JNIEXPORT jobject JNICALL Java_android_bluetooth_BlueZ_nativeCall(JNIEnv *env, jclass class, jstring jdestination, jstring jpath,
                                                                  jstring jinterface, jstring jmethod, jstring jsignature,
                                                                  jobjectArray jargs, jint timeout)
{
	if (!system_bus) {
		(*env)->ThrowNew(env, (*env)->FindClass(env, "java/io/IOException"), "no system bus");
		return NULL;
	}
	const char *destination = _CSTRING(jdestination);
	const char *path = _CSTRING(jpath);
	const char *interface = _CSTRING(jinterface);
	const char *method = _CSTRING(jmethod);
	const char *signature = _CSTRING(jsignature);

	GVariantBuilder builder;
	g_variant_builder_init(&builder, G_VARIANT_TYPE_TUPLE);
	const char *type_string = signature;
	for (jsize i = 0; *type_string; i++) {
		const char *end;
		if (!g_variant_type_string_scan(type_string, NULL, &end))
			break;
		char *type_copy = g_strndup(type_string, end - type_string);
		GVariantType *type = g_variant_type_new(type_copy);
		g_free(type_copy);
		jobject arg = (*env)->GetObjectArrayElement(env, jargs, i);
		GVariant *value = java_to_variant(env, arg, type);
		if (!value) { /* unsupported argument */
			g_variant_type_free(type);
			(*env)->DeleteLocalRef(env, arg);
			g_variant_builder_clear(&builder);
			(*env)->ReleaseStringUTFChars(env, jdestination, destination);
			(*env)->ReleaseStringUTFChars(env, jpath, path);
			(*env)->ReleaseStringUTFChars(env, jinterface, interface);
			(*env)->ReleaseStringUTFChars(env, jmethod, method);
			(*env)->ReleaseStringUTFChars(env, jsignature, signature);
			(*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), "argument does not fit the signature");
			return NULL;
		}
		g_variant_builder_add_value(&builder, value);
		g_variant_type_free(type);
		(*env)->DeleteLocalRef(env, arg);
		type_string = end;
	}

	GError *error = NULL;
	GVariant *reply = g_dbus_connection_call_sync(system_bus, destination, path, interface, method, g_variant_builder_end(&builder),
	                                              NULL, G_DBUS_CALL_FLAGS_NONE, timeout, NULL, &error);
	(*env)->ReleaseStringUTFChars(env, jdestination, destination);
	(*env)->ReleaseStringUTFChars(env, jpath, path);
	(*env)->ReleaseStringUTFChars(env, jinterface, interface);
	(*env)->ReleaseStringUTFChars(env, jmethod, method);
	(*env)->ReleaseStringUTFChars(env, jsignature, signature);
	if (!reply) {
		throw_io_exception(env, error);
		g_error_free(error);
		return NULL;
	}
	jobject result = NULL;
	gsize n = g_variant_n_children(reply);
	if (n == 1) {
		GVariant *child = g_variant_get_child_value(reply, 0);
		result = variant_to_java(env, child);
		g_variant_unref(child);
	} else if (n > 1) {
		result = variant_to_java(env, reply);
	}
	g_variant_unref(reply);
	return result;
}

/* pairing agent (android.bluetooth.PairingAgent): BlueZ calls org.bluez.Agent1 methods on the signal thread,
 * Java answers each invocation later with nativeReply, after the user decided in a dialog */

static const char agent_xml[] =
	"<node><interface name='org.bluez.Agent1'>"
	"<method name='Release'/>"
	"<method name='RequestPinCode'><arg type='o' direction='in'/><arg type='s' direction='out'/></method>"
	"<method name='DisplayPinCode'><arg type='o' direction='in'/><arg type='s' direction='in'/></method>"
	"<method name='RequestPasskey'><arg type='o' direction='in'/><arg type='u' direction='out'/></method>"
	"<method name='DisplayPasskey'><arg type='o' direction='in'/><arg type='u' direction='in'/><arg type='q' direction='in'/></method>"
	"<method name='RequestConfirmation'><arg type='o' direction='in'/><arg type='u' direction='in'/></method>"
	"<method name='RequestAuthorization'><arg type='o' direction='in'/></method>"
	"<method name='AuthorizeService'><arg type='o' direction='in'/><arg type='s' direction='in'/></method>"
	"<method name='Cancel'/>"
	"</interface></node>";

static void agent_method_call(GDBusConnection *connection, const char *sender, const char *path, const char *interface,
                              const char *method, GVariant *parameters, GDBusMethodInvocation *invocation, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	const char *device = NULL;
	jobject jarg = NULL;
	if (g_variant_n_children(parameters) >= 1) {
		GVariant *first = g_variant_get_child_value(parameters, 0);
		if (g_variant_is_of_type(first, G_VARIANT_TYPE_OBJECT_PATH))
			device = g_variant_get_string(first, NULL);
		if (g_variant_n_children(parameters) >= 2) {
			GVariant *second = g_variant_get_child_value(parameters, 1);
			jarg = variant_to_java(env, second);
			g_variant_unref(second);
		}
		g_variant_unref(first);
	}
	jstring jmethod = _JSTRING(method);
	jstring jdevice = device ? _JSTRING(device) : NULL;
	/* the invocation is owned by Java until nativeReply */
	J__PairingAgent__onRequest(env, _INTPTR(invocation), jmethod, jdevice, jarg);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	(*env)->DeleteLocalRef(env, jmethod);
	if (jdevice)
		(*env)->DeleteLocalRef(env, jdevice);
	if (jarg)
		(*env)->DeleteLocalRef(env, jarg);
}

static const GDBusInterfaceVTable agent_vtable = {agent_method_call, NULL, NULL};

#define AGENT_PATH "/org/atl/bluetooth/agent"
static gint agent_state = 0; /* 0 pending, 1 exported, -1 failed */

static void export_agent(void)
{
	GError *error = NULL;
	GDBusNodeInfo *info = g_dbus_node_info_new_for_xml(agent_xml, &error);
	guint id = 0;
	if (info) {
		id = g_dbus_connection_register_object(system_bus, AGENT_PATH, info->interfaces[0], &agent_vtable, NULL, NULL, &error);
		g_dbus_node_info_unref(info);
	}
	if (!id) {
		fprintf(stderr, "BlueZ agent: %s\n", error->message);
		g_error_free(error);
	}
	g_atomic_int_set(&agent_state, id ? 1 : -1);
}

JNIEXPORT jboolean JNICALL Java_android_bluetooth_PairingAgent_nativeExport(JNIEnv *env, jclass class, jstring jpath)
{
	/* the signal thread exports the object at AGENT_PATH right after it starts */
	for (int i = 0; i < 100 && system_bus && g_atomic_int_get(&agent_state) == 0; i++)
		g_usleep(10000);
	return g_atomic_int_get(&agent_state) == 1;
}

JNIEXPORT void JNICALL Java_android_bluetooth_PairingAgent_nativeReply(JNIEnv *env, jclass class, jlong jinvocation, jstring jerror, jobject jvalue)
{
	GDBusMethodInvocation *invocation = _PTR(jinvocation);
	if (jerror) {
		const char *name = _CSTRING(jerror);
		g_dbus_method_invocation_return_dbus_error(invocation, name, "Rejected");
		(*env)->ReleaseStringUTFChars(env, jerror, name);
	} else if (jvalue && is_instance(env, jvalue, "java/lang/String")) { /* RequestPinCode */
		const char *pin = _CSTRING(jvalue);
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", pin));
		(*env)->ReleaseStringUTFChars(env, jvalue, pin);
	} else if (jvalue) { /* RequestPasskey */
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(u)", (guint32)number_value(env, jvalue)));
	} else {
		g_dbus_method_invocation_return_value(invocation, NULL);
	}
}
