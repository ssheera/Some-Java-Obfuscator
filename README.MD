# XenonGuard
 A modified version of cheatbreaker-obf to test out some ideas I had
## Features
 * StripTransformer - `Strips debug information from classes, methods and fields`
 * ChecksumTransformer - `Inserts block of code inside configurable class which will check if the target method is modified, uses modified asm to check constant pool and method bytecode`
 * InlinerTransformer - `Tries to inline methods, does need massive improvements`
 * VariableTransformer - `Just converted local variables to java.lang.Object[]`
 * ConstantPoolTransformer - `Uses native method to modify constant pool`
 * ToStringTransformer - `Saw something cool in a source obfuscator and decided to implement it, converts strins to new Object() { toString... return "String" }.toString()`
##Usage
 You use the obfuscator by creating a config file much like the one provided and parsing it through command line
> java -jar obfuscator.jar --config config.yml
##Screenshots
###String Obfuscation
<img title="String Obfuscation" src="https://cdn.upload.systems/uploads/X9WPkFsw.png" alt="String Obfuscation"></img>
###Variable Obfuscation
<img title="Variable Obfuscation" src="https://cdn.upload.systems/uploads/E2E4ibjY.png" alt="Variable Obfuscation"></img>
###Inliner Obfuscation
<img title="Inliner Obfuscation" src="https://cdn.upload.systems/uploads/0Qyz0zDZ.png" alt="Inliner Obfuscation"></img>
###Checksum Obfuscation
<img title="Checksum Obfuscation" src="https://cdn.upload.systems/uploads/90Uoc5Lo.png" alt="Checksum Obfuscation"></img>
###ConstantPool Obfuscation
<img title="ConstantPool Obfuscation" src="https://cdn.upload.systems/uploads/wVdzaPsR.png" alt="ConstantPool Obfuscation"></img>

## Credits
 * cheatbreaker-obf for base
 * most of utils methods aren't mine
 * yaml classes for config stolen from bukkit
 