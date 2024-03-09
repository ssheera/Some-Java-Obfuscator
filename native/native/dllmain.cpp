#include <Windows.h>

#include <jni.h>
#include <jvmti.h>

#include "jvm.hpp"
#include "types.hpp"

namespace Offsets {
    uintptr_t klassOffset = 0;
}


static jintArray JNICALL raw_1bytes(JNIEnv* env, jclass  caller, jclass target, jstring method_name_and_sign_jstr) {
    
    auto klass = *reinterpret_cast<Klass**>(*(uintptr_t*)target + Offsets::klassOffset);

    auto method_name_and_sign = env->GetStringUTFChars(method_name_and_sign_jstr, 0);
    auto method =  klass->findMethod(method_name_and_sign);
    if (!method) return 0;
    auto c_method = method->get_constMethod();
    auto j = 0;

    auto code_size = c_method->get_code_size();
    auto arr = env->NewIntArray(code_size);
    auto code_base = *reinterpret_cast<char**>(c_method + 1);
    while (j < code_size) {
        auto bcp = code_base + j;
        auto bci = *(int*)bcp & 0xff;
        env->SetIntArrayRegion(arr, j++, 1, reinterpret_cast<jint*>(&bci));
    }

    return arr;
}

static void JNICALL decryptConstantPool(JNIEnv* env, jclass cls, jclass target) {
    auto klass = reinterpret_cast<Klass*>(*(uintptr_t*)target + Offsets::klassOffset);
    auto cp = klass->get_constants();
    auto tags = cp->get_tags();
    auto cls_name = klass->get_name()->to_string();
    int h = 0;
    if (cls_name.length() > 0) {

        for (int i = 0; i < cls_name.length(); i++) {
            h = 31 * h + (int)cls_name[i];
        }
    }
    auto base = cp->get_base();
    for (auto i = 0; i < tags->get_length(); i++) {
        auto tag = (constantTag)tags->at(i);
        if (tag.is_int()) {
            jint* val = (jint*)&base[i];
            *val ^= h;
            *val += (h << 2);
        }
        else if (tag.is_long()) {
            jlong* val = (jlong*)&base[i];
            *val += h;
            *val ^= (jlong(h) << 4);
        }
        else if (tag.is_double()) {
            jdouble* val = (jdouble*)&base[i];
            *val = cbrt(*val);
        }
        else if (tag.is_float()) {
            jfloat* val = (jfloat*)&base[i];
            *val = cbrtf(*val);
        }
    }
    return;
}

#define DEBUG

#include <iostream>
extern "C" JNIIMPORT VMStructEntry * gHotSpotVMStructs;
extern "C" JNIIMPORT VMTypeEntry * gHotSpotVMTypes;
extern "C" JNIIMPORT VMIntConstantEntry * gHotSpotVMIntConstants;
extern "C" JNIIMPORT VMLongConstantEntry * gHotSpotVMLongConstants;

static bool InitOffsets() {
    auto java_lang_Class = VMTypes::findTypeFields("java_lang_Class");
    if (!java_lang_Class.has_value()) return false;
    Offsets::klassOffset = *(jint*)java_lang_Class.value().get()["_klass_offset"]->address;
#ifdef DEBUG
    std::cout << "KlassOffset : " << Offsets::klassOffset << std::endl;
#endif // DEBUG

}


static bool Init(JNIEnv* env) {
    VMTypes::init(gHotSpotVMStructs, gHotSpotVMTypes,gHotSpotVMIntConstants,gHotSpotVMLongConstants);
    if (!InitOffsets()) return false;

    auto nativeHandler = env->FindClass("vm/NativeHandler");
    if (!nativeHandler) return false;
    JNINativeMethod table[] = {
            {(char*)"decryptConstantPool", (char*)"(Ljava/lang/Class;)V", (void*)&decryptConstantPool},
            {(char*)"raw_1bytes", (char*)"(Ljava/lang/Class;Ljava/lang/String;)[I", (void*)&raw_1bytes},
    };
    
    env->RegisterNatives(nativeHandler, table, sizeof(table) / sizeof(JNINativeMethod));

    return true;
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env{};
    auto err = vm->GetEnv((void**)&env, JNI_VERSION_1_8);
    if (err != JNI_OK || !env || !Init(env)) return JNI_EVERSION;
    return JNI_VERSION_1_8;
}


BOOL APIENTRY DllMain( HMODULE hModule,
                       DWORD  ul_reason_for_call,
                       LPVOID lpReserved
                     )
{
    switch (ul_reason_for_call)
    {
    case DLL_PROCESS_ATTACH:
    case DLL_THREAD_ATTACH:
    case DLL_THREAD_DETACH:
    case DLL_PROCESS_DETACH:
        break;
    }
    return TRUE;
}

