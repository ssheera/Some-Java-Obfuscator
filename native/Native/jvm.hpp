#pragma once
#pragma warning(disable:26815)

//thanks to github.com/rdbo/jnihook

#ifndef JVM_HPP
#define JVM_HPP

#include <unordered_map>
#include <optional>
#include <string>
#include <cstdint>

/* JVM definitions */
typedef struct {
	const char* typeName;
	const char* fieldName;
	const char* typeString;
	int32_t isStatic;
	uint64_t offset;
	void* address;
} VMStructEntry;

typedef struct {
	const char* typeName;
	const char* superclassName;
	int32_t isOopType;
	int32_t isIntegerType;
	int32_t isUnsigned;
	uint64_t size;
} VMTypeEntry;

typedef struct {
	const char* name;
	int32_t value;
} VMIntConstantEntry;

typedef struct {
	const char* name;
	uint64_t value;
} VMLongConstantEntry;

/* AccessFlags */
enum {
	JVM_ACC_NOT_C2_COMPILABLE = 0x02000000,
	JVM_ACC_NOT_C1_COMPILABLE = 0x04000000,
	JVM_ACC_NOT_C2_OSR_COMPILABLE = 0x08000000
};

/* VTable Index */
enum VtableIndexFlag {
	itable_index_max = -10,
	pending_itable_index = -9,
	invalid_vtable_index = -4,
	garbage_vtable_index = -3,
	nonvirtual_vtable_index = -2
};

/* Wrappers */
class VMTypes {
public:
	typedef std::unordered_map<std::string, VMStructEntry*> struct_entry_t;
	typedef std::unordered_map<std::string, struct_entry_t> struct_entries_t;
	typedef std::unordered_map<std::string, VMTypeEntry*> type_entries_t;
	typedef std::unordered_map<std::string, VMIntConstantEntry*> int_entries_t;
	typedef std::unordered_map<std::string, VMLongConstantEntry*> long_entries_t;
private:
	static struct_entries_t struct_entries;
	static type_entries_t type_entries;
	static int_entries_t int_entries;
	static long_entries_t long_entries;
public:
	static void init(VMStructEntry* vmstructs, VMTypeEntry* vmtypes, VMIntConstantEntry* vmints, VMLongConstantEntry* vmlongs);
	static std::optional<std::reference_wrapper<struct_entry_t>> findTypeFields(const char* typeName);
	static std::optional<VMTypeEntry*> findType(const char* typeName);
};

class VMType {
private:
	VMTypeEntry* type_entry;
	std::optional<std::reference_wrapper<VMTypes::struct_entry_t>> fields;
	void* instance; // pointer to instantiated VM type

	inline std::optional<void*> find_field_address(const char* fieldName)
	{
		auto tbl = fields.value().get();
		auto entry = tbl.find(fieldName);
		if (entry == tbl.end())
			return std::nullopt;

		auto field = entry->second;
		void* fieldAddress;
		if (field->isStatic)
			fieldAddress = (void*)field->address;
		else
			fieldAddress = (void*)((uint64_t)this->instance + field->offset);

		return fieldAddress;
	}

public:
	// the following function will lookup the type in the
	// VMTypes. If it is found, return successful std::optional
	static std::optional<VMType> from_instance(const char* typeName, void* instance);

	template <typename T>
	std::optional<T*> get_field(const char* fieldName)
	{
		auto fieldAddress = find_field_address(fieldName);
		if (!fieldAddress.has_value())
			return std::nullopt;

		return reinterpret_cast<T*>(fieldAddress.value());
	}

	inline void* get_instance()
	{
		return this->instance;
	}

	uint64_t size() {
		return (uint64_t)this->type_entry->size;
	}
};

#endif