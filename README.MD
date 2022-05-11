# XenonGuard
 A modified version of cheatbreaker-obf to test out some ideas I had
## Features
 * StripTransformer - `Strips debug information from classes, methods and fields`
 * ChecksumTransformer - `Inserts block of code inside configurable class which will check if the target method is modified, uses modified asm to check constant pool and method bytecode`
 * InlinerTransformer - `Tries to inline methods, does need massive improvements`
 * VariableTransformer - `Just converted local variables to java.lang.Object[]`
 * ConstantPoolTransformer - `Uses native method to modify constant pool`
 * ToStringTransformer - `Saw something cool in a source obfuscator and decided to implement it, converts strins to new Object() { toString... return "String" }.toString()`
## Credits
 * cheatbreaker-obf for base
 * most of utils methods aren't mine
 * yaml classes for config stolen from bukkit
 