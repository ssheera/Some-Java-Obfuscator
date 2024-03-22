#include "jvm.hpp"

//thanks to github.com/Lefraudeur/RiptermsGhost/blob/master/HotSpot/HotSpot.cpp

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
    constantTag(unsigned char tag) : _tag(tag) {}
    unsigned char _tag;
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




template <typename T>
class Array
{
public:
    int64_t get_length() {
        if (!this) return 0;
        if (sizeof(T) != 0x08)
        {
            static auto  typeArray = VMTypes::findTypeFields("Array<Klass*>"); 
            if (!typeArray.has_value()) return 0;
            static auto lengthEntry = typeArray.value().get()["_length"];
            return (int64_t)*(int*)((uintptr_t)this + lengthEntry->offset);
        }
        else {
            static auto  typeArray = VMTypes::findTypeFields("Array<Klass*>");
            if (!typeArray.has_value()) return 0;
            static auto lengthEntry = typeArray.value().get()["_length"];

            return *(int64_t*)((uintptr_t)this + lengthEntry->offset);
        }
    }

    T* get_data() {
        if (!this) return nullptr;
        if (sizeof(T) != 0x08) {
            static auto  typeArray = VMTypes::findTypeFields("Array<u2>");
            if (!typeArray.has_value()) return nullptr;
            static auto dataEntry = typeArray.value().get()["_data"];
            return (T*)((uintptr_t)this + dataEntry->offset);
        }
        else {
            static auto  typeArray = VMTypes::findTypeFields("Array<Klass*>");
            if (!typeArray.has_value()) return nullptr;
            static auto dataEntry = typeArray.value().get()["_data"];
            return (T*)((uintptr_t)this + dataEntry->offset);
        }

    }
    auto at(int i) -> T {
        if (i >= 0 && i < this->get_length())
            return (T)(this->get_data()[i]);
        return (T)NULL;
    }

    auto is_empty() const -> bool {
        return get_length() == 0;
    }

    T* adr_at(const int i) {
        if (i >= 0 && i < this->get_length())
            return &this->get_data()[i];
        return nullptr;
    }
};

class Symbol
{
public:
    std::string to_string();
};


class ConstantPool
{
public:
    void** get_base();
    Array<unsigned char>* get_tags();
    int get_length();

};
class ConstMethod
{
public:
    ConstantPool* get_constants();
    unsigned short get_code_size();
    unsigned short get_name_index();
    unsigned short get_signature_index();
};

class Method
{
public:
    ConstMethod* get_constMethod();
    std::string get_signature();
    std::string get_name();
};

class Klass
{
public:

    Symbol* get_name();
    Method* findMethod(const std::string& method_name, const std::string& method_sig);
    Method* findMethod(const std::string& name_and_sign);
    Array<Method*>* get_methods();
    ConstantPool* get_constants();
};