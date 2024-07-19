#include "types.hpp"



std::string Symbol::to_string()
{
    auto typeSymbol = VMType::from_instance("Symbol", this).value();
    int16_t len = *typeSymbol.get_field<int16_t>("_length").value();
    char* body = typeSymbol.get_field<char>("_body").value();

    return std::string(body, len);
}

void** ConstantPool::get_base()
{
    if (!this) return nullptr;

    static VMTypeEntry* ConstantPool_entry = VMTypes::findType("ConstantPool").value();
    if (!ConstantPool_entry) return nullptr;

    return (void**)((uint8_t*)this + ConstantPool_entry->size);
}

Array<unsigned char>* ConstantPool::get_tags()
{
    auto contatnPool = VMType::from_instance("ConstantPool", this).value();
    auto tag = *contatnPool.get_field<void*>("_tags").value();
    return reinterpret_cast<Array<unsigned char>*>(tag);
}

int ConstantPool::get_length()
{
    if (!this) return 0;

    static VMStructEntry* length_entry = VMTypes::findTypeFields("ConstantPool").value().get()["_length"];
    if (!length_entry) return 0;

    return *(int*)((uint8_t*)this + length_entry->offset);
    return 0;
}

ConstantPool* ConstMethod::get_constants()
{
    if (!this) return nullptr;

    static VMStructEntry* _constants_entry = VMTypes::findTypeFields("ConstMethod").value().get()["_constants"];
    if (!_constants_entry) return nullptr;

    return *(ConstantPool**)((uint8_t*)this + _constants_entry->offset);
}

unsigned short ConstMethod::get_code_size()
{
    if (!this) return 0;

    static VMStructEntry* _code_size_entry = VMTypes::findTypeFields("ConstMethod").value().get()["_code_size"];
    if (!_code_size_entry) return 0;

    return *(unsigned short*)((uint8_t*)this + _code_size_entry->offset);
}

unsigned short ConstMethod::get_name_index()
{
    if (!this) return 0;

    static VMStructEntry* _name_index_entry = VMTypes::findTypeFields("ConstMethod").value().get()["_name_index"];
    if (!_name_index_entry) return 0;

    return *(unsigned short*)((uint8_t*)this + _name_index_entry->offset);
}

unsigned short ConstMethod::get_signature_index()
{
    if (!this) return 0;

    static VMStructEntry* _signature_index_entry = VMTypes::findTypeFields("ConstMethod").value().get()["_signature_index"];
    if (!_signature_index_entry) return 0;

    return *(unsigned short*)((uint8_t*)this + _signature_index_entry->offset);
}

ConstMethod* Method::get_constMethod()
{
    if (!this) return nullptr;

    static VMStructEntry* _constMethod_entry = VMTypes::findTypeFields("Method").value().get()["_constMethod"];
    if (!_constMethod_entry) return nullptr;

    return *(ConstMethod**)((uint8_t*)this + _constMethod_entry->offset);
}

std::string Method::get_signature()
{
    if (!this) return "";

    ConstMethod* const_method = this->get_constMethod();
    auto signature_index = const_method->get_signature_index();
    ConstantPool* cp = const_method->get_constants();
    Symbol** base = (Symbol**)cp->get_base();
    return base[signature_index]->to_string();
}

std::string Method::get_name()
{
    if (!this) return "";

    ConstMethod* const_method = this->get_constMethod();
    auto signature_index = const_method->get_name_index();
    ConstantPool* cp = const_method->get_constants();
    Symbol** base = (Symbol**)cp->get_base();
    return base[signature_index]->to_string();
}

Array<Method*>* Klass::get_methods()
{
    if (!this) return nullptr;

    static VMStructEntry* _methods_entry = VMTypes::findTypeFields("InstanceKlass").value().get()["_methods"];
    if (!_methods_entry) return nullptr;

    return *(Array<Method*>**)((uint8_t*)this + _methods_entry->offset);
}

ConstantPool* Klass::get_constants()
{
    if (!this) return nullptr;

    static VMStructEntry* _constants_entry = VMTypes::findTypeFields("InstanceKlass").value().get()["_constants"];
    if (!_constants_entry) return nullptr;

    return *(ConstantPool**)((uint8_t*)this + _constants_entry->offset);
}


Symbol* Klass::get_name()
{
    if (!this) return nullptr;

    static VMStructEntry* _name_entry = VMTypes::findTypeFields("Klass").value().get()["_name"];
    if (!_name_entry) return nullptr;

    return *(Symbol**)((uint8_t*)this + _name_entry->offset);
}

Method* Klass::findMethod(const std::string& method_name, const std::string& method_sig)
{
    auto _methods = get_methods();
    auto _data = _methods->get_data();
    auto _length = _methods->get_length();
    for (int i = 0; i < _length; ++i)
    {
        auto method = _data[i];
        auto cm = method->get_constMethod();
        auto cp = cm->get_constants();
        auto b = cp->get_base();
        auto n = cm->get_name_index();
        auto s = cm->get_signature_index();
        auto symbol = (Symbol*)b[n];
        auto symbol_sig = (Symbol*)b[s];
        const auto& name = symbol->to_string();
        const auto& signature = symbol_sig->to_string();
        if (name == method_name && signature == method_sig)
            return method;
    }
    return nullptr;
}

Method* Klass::findMethod(const std::string& name_and_sign)
{
    auto _methods = get_methods();
    auto _data = _methods->get_data();
    auto _length = _methods->get_length();
    for (int i = 0; i < _length; ++i)
    {
        auto method = _data[i];
        auto cm = method->get_constMethod();
        auto cp = cm->get_constants();
        auto b = cp->get_base();
        auto n = cm->get_name_index();
        auto s = cm->get_signature_index();
        auto symbol = (Symbol*)b[n];
        auto symbol_sig = (Symbol*)b[s];
        const auto& comp = symbol->to_string() + symbol_sig->to_string();
        if (comp == name_and_sign)
            return method;
    }
    return nullptr;
}

