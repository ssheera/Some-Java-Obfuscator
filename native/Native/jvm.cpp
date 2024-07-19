
#include "jvm.hpp"
#include <string>


#include <iostream>

#include <jvmti.h>

/* VMTypes */

#define DumpSDK

VMTypes::struct_entries_t VMTypes::struct_entries;
VMTypes::type_entries_t VMTypes::type_entries;
VMTypes::int_entries_t VMTypes::int_entries;
VMTypes::long_entries_t VMTypes::long_entries;

void VMTypes::init(VMStructEntry* vmstructs, VMTypeEntry* vmtypes, VMIntConstantEntry* vmints, VMLongConstantEntry* vmlongs)
{
	for (int i = 0; vmstructs[i].fieldName != NULL; ++i) {
		auto s = &vmstructs[i];
#ifdef DumpSDK
		std::cout << "VMStructEntry: \n"
			"type: " << s->typeName << "\n"
			"field: " << s->fieldName << "\n"
			"static: " << (s->isStatic ? "true" : "false") << "\n";
		if (s->isStatic) std::cout <<
			"address: " << s->address << "\n";
		std::cout << "offset: " << s->offset << "\n\n";

#endif // DumpSDK
		VMTypes::struct_entries[s->typeName][s->fieldName] = s;
	}

	for (int i = 0; vmtypes[i].typeName != NULL; ++i) {
		auto t = &vmtypes[i];
#ifdef DumpSDK
		std::cout << "VMType :" << t->typeName << "\nSize :" << t->size << "\n\n";
#endif // DumpSDK
		VMTypes::type_entries[t->typeName] = t;
	}


	for (int i = 0; vmints[i].name != NULL; ++i) {
		auto v = &vmints[i];
#ifdef DumpSDK
		std::cout << "VMInt :" << v->name << "\nValue :" << v->value << "\n\n";
#endif // DumpSDK
		VMTypes::int_entries[v->name] = v;
	}

	for (int i = 0; vmlongs[i].name != NULL; ++i) {
		auto l = &vmlongs[i];
#ifdef DumpSDK
		std::cout << "VMLong :" << l->name << "\nValue :" << l->value << "\n\n";
#endif // DumpSDK
		VMTypes::long_entries[l->name] = l;
	}
}

std::optional<std::reference_wrapper<VMTypes::struct_entry_t>> VMTypes::findTypeFields(const char* typeName)
{
	for (auto& m : VMTypes::struct_entries) {
		if (m.first == typeName)
			return m.second;
	}

	return std::nullopt;
}

std::optional<VMTypeEntry*> VMTypes::findType(const char* typeName)
{
	auto t = type_entries.find(typeName);
	if (t == type_entries.end())
		return std::nullopt;

	return t->second;
}

/* VMType */
std::optional<VMType> VMType::from_instance(const char* typeName, void* instance)
{
	auto type = VMTypes::findType(typeName);
	if (!type.has_value())
		return std::nullopt;

	auto fields = VMTypes::findTypeFields(typeName);
	if (!fields.has_value())
		return std::nullopt;

	VMType vmtype;
	vmtype.instance = instance;
	vmtype.type_entry = type.value();
	vmtype.fields = fields;

	return vmtype;
}