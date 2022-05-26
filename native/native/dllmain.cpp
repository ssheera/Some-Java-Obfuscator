// dllmain.cpp : Defines the entry point for the DLL application.
#include "pch.h"
#include "jni.h"
#include <iostream>

#define offset(a, b) uintptr_t(uintptr_t(a) + b)
#define poffset(a, b) *(uintptr_t*) offset(a, b)

template <typename T>
class Array {
public:
	int _length;
	T   _data[1];

	T    at(int i) const { return _data[i]; }
};

enum {
	JVM_CONSTANT_Utf8 = 1,
	JVM_CONSTANT_Unicode = 2, /* unused */
	JVM_CONSTANT_Integer = 3,
	JVM_CONSTANT_Float = 4,
	JVM_CONSTANT_Long = 5,
	JVM_CONSTANT_Double = 6,
	JVM_CONSTANT_Class = 7,
	JVM_CONSTANT_String = 8,
	JVM_CONSTANT_Fieldref = 9,
	JVM_CONSTANT_Methodref = 10,
	JVM_CONSTANT_InterfaceMethodref = 11,
	JVM_CONSTANT_NameAndType = 12,
	JVM_CONSTANT_MethodHandle = 15,  // JSR 292
	JVM_CONSTANT_MethodType = 16,   // JSR 292
	JVM_CONSTANT_Dynamic = 17,
	JVM_CONSTANT_InvokeDynamic = 18
};

class constantTag {
public:
	signed char _tag;
public:
	bool is_klass() const { return _tag == JVM_CONSTANT_Class; }
	bool is_field() const { return _tag == JVM_CONSTANT_Fieldref; }
	bool is_method() const { return _tag == JVM_CONSTANT_Methodref; }
	bool is_interface_method() const { return _tag == JVM_CONSTANT_InterfaceMethodref; }
	bool is_string() const { return _tag == JVM_CONSTANT_String; }
	bool is_int() const { return _tag == JVM_CONSTANT_Integer; }
	bool is_float() const { return _tag == JVM_CONSTANT_Float; }
	bool is_long() const { return _tag == JVM_CONSTANT_Long; }
	bool is_double() const { return _tag == JVM_CONSTANT_Double; }
	bool is_name_and_type() const { return _tag == JVM_CONSTANT_NameAndType; }
	bool is_utf8() const { return _tag == JVM_CONSTANT_Utf8; }

};

class Symbol {
public:

	short _length;
	short _refcount;
	int _identity_hash;

	char _body[1];

	std::string str() {
		return std::string(_body, _length);
	}
};

class ConstMethod {
public:
	char size[0x30];

	char* code_base() const { return (char*)(this + 1); }
};

// Helper method 
extern "C"
JNIEXPORT jintArray JNICALL Java_vm_NativeHandler_raw_1bytes(JNIEnv * env, jclass cls, jclass target, jstring jm) {

	auto holder = *(uintptr_t*)target;
	auto klass = poffset(holder, 0x48);

	auto method_name = env->GetStringUTFChars(jm, 0);

	auto cp = poffset(klass, 0xD8);

	auto class_size = 0x50;

	auto base = (intptr_t*)(((char*)cp) + class_size);

	auto methods = reinterpret_cast<Array<void*>*>(poffset(klass, 0x180));

	for (auto i = 0; i < methods->_length; i++) {
		auto method = methods->at(i);

		auto c_method = (ConstMethod*) poffset(method, 0x8);

		auto name_index = unsigned short(poffset(c_method, 0x22));
		auto sig_index = unsigned short(poffset(c_method, 0x24));

		auto name_sym = *(Symbol**)&base[name_index];
		auto sig_sym = *(Symbol**)&base[sig_index];
		
		auto name = name_sym->str().append(sig_sym->str());

		auto code_base = c_method->code_base();
		
		if (!strcmp(name.c_str(), method_name)) {

			auto j = 0;

			auto code_size = unsigned short(poffset(c_method, 0x20));
			auto arr = env->NewIntArray(code_size);

			while (j < code_size) {
				auto bcp = code_base + j;
				auto bci = *(int*)bcp & 0xff;
				env->SetIntArrayRegion(arr, j++, 1, reinterpret_cast<jint*>(&bci));
			}

			return arr;
		}
	}

	return 0;
}


extern "C"
JNIEXPORT void JNICALL Java_vm_NativeHandler_transformMethod(JNIEnv * env, jclass, jclass target, jstring jm, jintArray jb) {

	auto holder = *(uintptr_t*)target;
	auto klass = poffset(holder, 0x48);

	auto method_name = env->GetStringUTFChars(jm, 0);

	auto cp = poffset(klass, 0xD8);

	auto class_size = 0x50;

	auto base = (intptr_t*)(((char*)cp) + class_size);

	auto methods = reinterpret_cast<Array<void*>*>(poffset(klass, 0x180));

	auto bytes_size = env->GetArrayLength(jb);

	jint* _code = env->GetIntArrayElements(jb, 0);

	for (auto i = 0; i < methods->_length; i++) {
		auto method = methods->at(i);

		auto c_method = (ConstMethod*)poffset(method, 0x8);

		auto name_index = unsigned short(poffset(c_method, 0x22));
		auto sig_index = unsigned short(poffset(c_method, 0x24));

		auto name_sym = *(Symbol**)&base[name_index];
		auto sig_sym = *(Symbol**)&base[sig_index];

		auto name = name_sym->str().append(sig_sym->str());

		auto code_base = c_method->code_base();

		if (!strcmp(name.c_str(), method_name)) {

			auto code_size = unsigned short(poffset(c_method, 0x20));

			if (code_size != bytes_size) {
				poffset(c_method, 0x20) = bytes_size;
			}

			for (auto j = 0; j < bytes_size; j++) {
				*(code_base + j) = _code[j];
			}

		}
	}

}

extern "C"
JNIEXPORT void JNICALL Java_vm_NativeHandler_decryptConstantPool(JNIEnv * env, jclass cls, jclass target) {

	// JAVA 8 HOTSPOT
	// add other versions yourself

	auto holder = *(uintptr_t*)target;
	auto klass = poffset(holder, 0x48);//offset to InstanceKlass
	auto cp = poffset(klass, 0xD8);//offset to ConstantPool
	auto tags = reinterpret_cast<Array<constantTag>*>(poffset(cp, 0x8));//offset to Array<constantTag>

	auto class_size = 0x50;//sizeof ConstantPool
	auto cp_len = int(poffset(cp, 0x3C));//offset to _length

	auto base = (intptr_t*)(((char*)cp) + class_size);

	// 0x10 offset to name
	auto cls_name = reinterpret_cast<Symbol*>(poffset(klass, 0x10))->str();

	int h = 0;
	if (cls_name.length() > 0) {

		for (int i = 0; i < cls_name.length(); i++) {
			h = 31 * h + (int)cls_name[i];
		}
	}

	for (auto i = 0; i < cp_len; i++) {
		auto tag = tags->at(i);
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

}

