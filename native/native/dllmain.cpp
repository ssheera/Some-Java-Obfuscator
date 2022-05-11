// dllmain.cpp : Defines the entry point for the DLL application.
#include "pch.h"

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

class jclass {};

extern "C"
__declspec(dllexport) void __stdcall Java_vm_NativeHandler_decryptConstantPool(void * env, jclass * cls, jclass * target) {

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
			long* val = (long*)&base[i];
			*val ^= h;
		}
		else if (tag.is_long()) {
			long long* val = (long long*)&base[i];
			*val ^= (long long(h) << 4);
		}
		else if (tag.is_double()) {
			double* val = (double*)&base[i];
			*val = cbrt(*val);
		}
		else if (tag.is_float()) {
			float* val = (float*)&base[i];
			*val = cbrtf(*val);
		}
	}

}

