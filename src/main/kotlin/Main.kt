data class Registers(
    var a: Int = 0,
    var b: Int = 0,
    var c: Int = 0,
    var d: Int = 0,
    var e: Int = 0,
    var h: Int = 0,
    var l: Int = 0,
    var sp: Int = 0,
    var pc: Int = 0
)

enum class Flag {
    Sign,
    Zero,
    X5,
    AuxiliaryCarry,
    X3,
    Parity,
    X1,
    Carry
}

var cycleCounter = 0
var flags = 2
var registers = Registers()
var memory = ByteArray(65536)
var stack = ByteArray(65536)

// Memory access
fun readByte(address: Int): Int {
    return memory[address].toInt() and 0xFF
}

fun writeByte(address: Int, value: Int) {
    memory[address] = value.toByte()
}

fun readStack(address: Int): Int {
    return stack[address].toInt() and 0xFF
}

fun writeStack(address: Int, value: Int) {
    stack[address] = value.toByte()
}

fun setFlag(flag: Flag, bool: Boolean) {
    flags = if (bool) flags or (1 shl flag.ordinal)
    else flags and (1 shl flag.ordinal).inv()
}

fun resetFlag(flag: Flag) {
    flags = flags and (1 shl flag.ordinal).inv()
}

fun isFlagSet(flag: Flag): Boolean {
    return (flags and (1 shl flag.ordinal)) != 0
}

fun checkParity(value: Int): Boolean {
    var count = 0
    var temp = value
    while (temp != 0) {
        count++
        temp = temp and (temp - 1)
    }
    return count % 2 == 0
}

// Instruction decoding
fun decodeInstruction(opcode: Int): Instruction {
    return when (opcode) {
        0x00 -> Instruction("NOP", 1, 4) {}
        0x10 -> Instruction("NOP", 1, 4) {}
        0x20 -> Instruction("NOP", 1, 4) {}
        0x30 -> Instruction("NOP", 1, 4) {}

        0x01 -> Instruction("LXI, B,d16", 3, 10) { registers ->
            registers.c = readByte(registers.pc + 1)
            registers.b = readByte(registers.pc + 2)
        }
        0x11 -> Instruction("LXI, D,d16", 3, 10) { registers ->
            registers.e = readByte(registers.pc + 1)
            registers.d = readByte(registers.pc + 2)
        }
        0x21 -> Instruction("LXI, H,d16", 3, 10) { registers ->
            registers.l = readByte(registers.pc + 1)
            registers.h = readByte(registers.pc + 2)
        }
        0x31 -> Instruction("LXI, SP,d16", 3, 10) { registers ->
            registers.sp = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
        }

        0x02 -> Instruction("STAX B", 1, 7) { registers ->
            val address = (registers.b shl 8) or registers.c
            writeByte(address, registers.a)
        }
        0x12 -> Instruction("STAX D", 1, 7) { registers ->
            val address = (registers.d shl 8) or registers.e
            writeByte(address, registers.a)
        }
        0x22 -> Instruction("SHLD a16", 3, 16) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            writeByte(address, registers.l)
            writeByte(address + 1, registers.h)
        }
        0x32 -> Instruction("STA a16", 3, 13) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            writeByte(address, registers.a)
        }

        0x03 -> Instruction("INX B", 1, 5) { registers ->
            val result = (registers.b shl 8) or registers.c + 1
            registers.b = (result shr 8) and 0xFF
            registers.c = result and 0xFF
        }
        0x13 -> Instruction("INX D", 1, 5) { registers ->
            val result = (registers.d shl 8) or registers.e + 1
            registers.d = (result shr 8) and 0xFF
            registers.e = result and 0xFF
        }
        0x23 -> Instruction("INX H", 1, 5) { registers ->
            val result = (registers.h shl 8) or registers.l + 1
            registers.h = (result shr 8) and 0xFF
            registers.l = result and 0xFF
        }
        0x33 -> Instruction("INX SP", 1, 5) { registers ->
            val result = (registers.sp + 1) and 0xFFFF
            registers.sp = result
        }

        0x04 -> Instruction("INR B", 1, 5) { registers -> registers.b = inr(registers.b) }
        0x14 -> Instruction("INR D", 1, 5) { registers -> registers.d = inr(registers.d) }
        0x24 -> Instruction("INR H", 1, 5) { registers -> registers.h = inr(registers.h) }
        0x34 -> Instruction("INR M", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, inr(readByte(address)))
        }

        0x05 -> Instruction("DCR B", 1, 5) { registers -> registers.b = dcr(registers.b) }
        0x15 -> Instruction("DCR D", 1, 5) { registers -> registers.d = dcr(registers.d) }
        0x25 -> Instruction("DCR H", 1, 5) { registers -> registers.h = dcr(registers.h) }
        0x35 -> Instruction("DCR M", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, dcr(readByte(address)))
        }

        0x06 -> Instruction("MVI, B,d8", 2, 7) { registers ->
            registers.b = readByte(registers.pc + 1)
        }
        0x16 -> Instruction("MVI, D,d8", 2, 7) { registers ->
            registers.d = readByte(registers.pc + 1)
        }
        0x26 -> Instruction("MVI, H,d8", 2, 7) { registers ->
            registers.h = readByte(registers.pc + 1)
        }
        0x36 -> Instruction("MVI, M,d8", 2, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, readByte(registers.pc + 1))
        }

        0x07 -> Instruction("RLC", 1, 4) { registers ->
            val highBit = registers.a shr 7
            registers.a = (registers.a shl 1) or highBit
            setFlag(Flag.Carry, highBit != 0)
        }
        0x17 -> Instruction("RAL", 1, 4) { registers ->
            val carry = isFlagSet(Flag.Carry)
            val highBit = registers.a shr 7
            registers.a = (registers.a shl 1) or (if (carry) 1 else 0)
            setFlag(Flag.Carry, highBit != 0)
        }
        0x27 -> Instruction("DAA", 1, 4) { registers ->
            var a = registers.a
            var correction = 0

            if ((a and 0x0F) > 9 || isFlagSet(Flag.AuxiliaryCarry)) {
                correction = 0x06
            }

            if (a > 0x99 || isFlagSet(Flag.Carry)) {
                correction = correction or 0x60
                setFlag(Flag.Carry, true)
            } else {
                setFlag(Flag.Carry, false)
            }

            a = (a + correction) and 0xFF

            setFlag(Flag.Sign, (a shr 7) != 0)
            setFlag(Flag.Zero, a == 0)
            setFlag(Flag.Parity, checkParity(a))

            registers.a = a
        }
        0x37 -> Instruction("STC", 1, 4) { setFlag(Flag.Carry, true) }

        0x08 -> Instruction("NOP", 1, 4) {}
        0x18 -> Instruction("NOP", 1, 4) {}
        0x28 -> Instruction("NOP", 1, 4) {}
        0x38 -> Instruction("NOP", 1, 4) {}

        0x09 -> Instruction("DAD B", 1, 10) { registers ->
            val result = ((registers.h shl 8) or registers.l) + ((registers.b shl 8) or registers.c)
            registers.h = (result shr 8) and 0xFF
            registers.l = result and 0xFF

            setFlag(Flag.Carry, result > 0xFFFF)
        }
        0x19 -> Instruction("DAD D", 1, 10) { registers ->
            val result = ((registers.h shl 8) or registers.l) + ((registers.d shl 8) or registers.e)
            registers.h = (result shr 8) and 0xFF
            registers.l = result and 0xFF

            setFlag(Flag.Carry, result > 0xFFFF)
        }
        0x29 -> Instruction("DAD H", 1, 10) { registers ->
            val result = ((registers.h shl 8) or registers.l) + ((registers.h shl 8) or registers.l)
            registers.h = (result shr 8) and 0xFF
            registers.l = result and 0xFF

            setFlag(Flag.Carry, result > 0xFFFF)
        }
        0x39 -> Instruction("DAD SP", 1, 10) { registers ->
            val result =
                ((registers.h shl 8) or registers.l) + (((registers.sp shr 8) shl 8) or (registers.sp and 0xFF))
            registers.h = (result shr 8) and 0xFF
            registers.l = result and 0xFF

            setFlag(Flag.Carry, result > 0xFFFF)
        }

        0x0A -> Instruction("LDAX B", 1, 7) { registers ->
            val address = (registers.b shl 8) or registers.c
            registers.a = readByte(address)
        }
        0x1A -> Instruction("LDAX D", 1, 7) { registers ->
            val address = (registers.d shl 8) or registers.e
            registers.a = readByte(address)
        }
        0x2A -> Instruction("LHLD a16", 3, 16) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            registers.l = readByte(address)
            registers.h = readByte(address + 1)
        }
        0x3A -> Instruction("LDA a16", 3, 13) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            registers.a = readByte(address)
        }

        0x0B -> Instruction("DCX B", 1, 5) { registers ->
            val result = (registers.b shl 8) or registers.c - 1
            registers.b = (result shr 8) and 0xFF
            registers.c = result and 0xFF
        }
        0x1B -> Instruction("DCX D", 1, 5) { registers ->
            val result = (registers.d shl 8) or registers.e - 1
            registers.d = (result shr 8) and 0xFF
            registers.e = result and 0xFF
        }
        0x2B -> Instruction("DCX H", 1, 5) { registers ->
            val result = (registers.h shl 8) or registers.l - 1
            registers.h = (result shr 8) and 0xFF
            registers.l = result and 0xFF
        }
        0x3B -> Instruction("DCX SP", 1, 5) { registers ->
            val result = (registers.sp - 1) and 0xFFFF
            registers.sp = result
        }

        0x0C -> Instruction("INR C", 1, 5) { registers -> registers.c = inr(registers.c) }
        0x1C -> Instruction("INR E", 1, 5) { registers -> registers.e = inr(registers.e) }
        0x2C -> Instruction("INR L", 1, 5) { registers -> registers.l = inr(registers.l) }
        0x3C -> Instruction("INR A", 1, 5) { registers -> registers.a = inr(registers.a) }

        0x0D -> Instruction("DCR C", 1, 5) { registers -> registers.c = dcr(registers.c) }
        0x1D -> Instruction("DCR E", 1, 5) { registers -> registers.e = dcr(registers.e) }
        0x2D -> Instruction("DCR L", 1, 5) { registers -> registers.l = dcr(registers.l) }
        0x3D -> Instruction("DCR A", 1, 5) { registers -> registers.a = dcr(registers.a) }

        0x0E -> Instruction("MVI C,d8", 2, 7) { registers ->
            registers.c = readByte(registers.pc + 1)
        }
        0x1E -> Instruction("MVI E,d8", 2, 7) { registers ->
            registers.e = readByte(registers.pc + 1)
        }
        0x2E -> Instruction("MVI L,d8", 2, 7) { registers ->
            registers.l = readByte(registers.pc + 1)
        }
        0x3E -> Instruction("MVI A,d8", 2, 7) { registers ->
            registers.a = readByte(registers.pc + 1)
        }

        0x0F -> Instruction("RRC", 1, 4) { registers ->
            val lowBit = registers.a and 0x01
            registers.a = (registers.a shr 1) or (lowBit shl 7)
            setFlag(Flag.Carry, lowBit != 0)
        }
        0x1F -> Instruction("RAR", 1, 4) { registers ->
            val carry = isFlagSet(Flag.Carry)
            val lowBit = registers.a and 0x01
            registers.a = (registers.a shr 1) or (if (carry) 1 else 0 shl 7)
            setFlag(Flag.Carry, lowBit != 0)
        }
        0x2F -> Instruction("CMA", 1, 4) { registers ->
            registers.a = registers.a.inv()
        }
        0x3F -> Instruction("CMC", 1, 4) {
            setFlag(Flag.Carry, !isFlagSet(Flag.Carry))
        }

        // MOV instructions
        0x40 -> Instruction("MOV, B,B", 1, 5) {}
        0x50 -> Instruction("MOV, D,B", 1, 5) { registers -> registers.d = registers.b }
        0x60 -> Instruction("MOV, H,B", 1, 5) { registers -> registers.h = registers.b }
        0x70 -> Instruction("MOV, M,B", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, registers.b)
        }

        0x41 -> Instruction("MOV, B,C", 1, 5) { registers -> registers.b = registers.c }
        0x51 -> Instruction("MOV, D,C", 1, 5) { registers -> registers.d = registers.c }
        0x61 -> Instruction("MOV, H,C", 1, 5) { registers -> registers.h = registers.c }
        0x71 -> Instruction("MOV, M,C", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, registers.c)
        }

        0x42 -> Instruction("MOV, B,D", 1, 5) { registers -> registers.b = registers.d }
        0x52 -> Instruction("MOV, D,D", 1, 5) {}
        0x62 -> Instruction("MOV, H,D", 1, 5) { registers -> registers.h = registers.d }
        0x72 -> Instruction("MOV, M,D", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, registers.d)
        }

        0x43 -> Instruction("MOV, B,E", 1, 5) { registers -> registers.b = registers.e }
        0x53 -> Instruction("MOV, D,E", 1, 5) { registers -> registers.d = registers.e }
        0x63 -> Instruction("MOV, H,E", 1, 5) { registers -> registers.h = registers.e }
        0x73 -> Instruction("MOV, M,E", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, registers.e)
        }

        0x44 -> Instruction("MOV, B,H", 1, 5) { registers -> registers.b = registers.h }
        0x54 -> Instruction("MOV, D,H", 1, 5) { registers -> registers.d = registers.h }
        0x64 -> Instruction("MOV, H,H", 1, 5) {}
        0x74 -> Instruction("MOV, M,H", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, registers.h)
        }

        0x45 -> Instruction("MOV, B,L", 1, 5) { registers -> registers.b = registers.l }
        0x55 -> Instruction("MOV, D,L", 1, 5) { registers -> registers.d = registers.l }
        0x65 -> Instruction("MOV, H,L", 1, 5) { registers -> registers.h = registers.l }
        0x75 -> Instruction("MOV, M,L", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, registers.l)
        }

        0x46 -> Instruction("MOV, B,M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            registers.b = readByte(address)
        }
        0x56 -> Instruction("MOV, D,M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            registers.d = readByte(address)
        }
        0x66 -> Instruction("MOV, H,M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            registers.h = readByte(address)
        }
        0x76 -> Instruction("HLT", 1, 7) {/* TODO: Остановка программы */}

        0x47 -> Instruction("MOV, B,A", 1, 5) { registers -> registers.b = registers.a }
        0x57 -> Instruction("MOV, D,A", 1, 5) { registers -> registers.d = registers.a }
        0x67 -> Instruction("MOV, H,A", 1, 5) { registers -> registers.h = registers.a }
        0x77 -> Instruction("MOV, M,A", 1, 5) { registers ->
            val address = (registers.h shl 8) or registers.l
            writeByte(address, registers.a)
        }

        0x48 -> Instruction("MOV, C,B", 1, 5) { registers -> registers.c = registers.b }
        0x58 -> Instruction("MOV, E,B", 1, 5) { registers -> registers.e = registers.b }
        0x68 -> Instruction("MOV, L,B", 1, 5) { registers -> registers.l = registers.b }
        0x78 -> Instruction("MOV, A,B", 1, 5) { registers -> registers.a = registers.b }

        0x49 -> Instruction("MOV, C,C", 1, 5) {}
        0x59 -> Instruction("MOV, E,C", 1, 5) { registers -> registers.e = registers.c }
        0x69 -> Instruction("MOV, L,C", 1, 5) { registers -> registers.l = registers.c }
        0x79 -> Instruction("MOV, A,C", 1, 5) { registers -> registers.a = registers.c }

        0x4A -> Instruction("MOV, C,D", 1, 5) { registers -> registers.c = registers.d }
        0x5A -> Instruction("MOV, E,D", 1, 5) { registers -> registers.e = registers.d }
        0x6A -> Instruction("MOV, L,D", 1, 5) { registers -> registers.l = registers.d }
        0x7A -> Instruction("MOV, A,D", 1, 5) { registers -> registers.a = registers.d }

        0x4B -> Instruction("MOV, C,E", 1, 5) { registers -> registers.c = registers.e }
        0x5B -> Instruction("MOV, E,E", 1, 5) {}
        0x6B -> Instruction("MOV, L,E", 1, 5) { registers -> registers.l = registers.e }
        0x7B -> Instruction("MOV, A,E", 1, 5) { registers -> registers.a = registers.e }

        0x4C -> Instruction("MOV, C,H", 1, 5) { registers -> registers.c = registers.h }
        0x5C -> Instruction("MOV, E,H", 1, 5) { registers -> registers.e = registers.h }
        0x6C -> Instruction("MOV, L,H", 1, 5) { registers -> registers.l = registers.h }
        0x7C -> Instruction("MOV, A,H", 1, 5) { registers -> registers.a = registers.h }

        0x4D -> Instruction("MOV, C,L", 1, 5) { registers -> registers.c = registers.l }
        0x5D -> Instruction("MOV, E,L", 1, 5) { registers -> registers.e = registers.l }
        0x6D -> Instruction("MOV, L,L", 1, 5) {}
        0x7D -> Instruction("MOV, A,L", 1, 5) { registers -> registers.a = registers.l }

        0x4E -> Instruction("MOV, C,M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            registers.c = readByte(address)
        }
        0x5E -> Instruction("MOV, E,M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            registers.e = readByte(address)
        }
        0x6E -> Instruction("MOV, L,M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            registers.l = readByte(address)
        }
        0x7E -> Instruction("MOV, A,M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            registers.a = readByte(address)
        }

        0x4F -> Instruction("MOV, C,A", 1, 5) { registers -> registers.c = registers.a }
        0x5F -> Instruction("MOV, E,A", 1, 5) { registers -> registers.e = registers.a }
        0x6F -> Instruction("MOV, L,A", 1, 5) { registers -> registers.l = registers.a }
        0x7F -> Instruction("MOV, A,A", 1, 5) {}

        // Arithmetic and bit logic instructions
        0x80 -> Instruction("ADD B", 1, 4) { registers -> add(registers, registers.b) }
        0x90 -> Instruction("SUB B", 1, 4) { registers -> sub(registers, registers.b) }
        0xA0 -> Instruction("ANA B", 1, 4) { registers -> ana(registers, registers.b) }
        0xB0 -> Instruction("ORA B", 1, 4) { registers -> ora(registers, registers.b) }

        0x81 -> Instruction("ADD C", 1, 4) { registers -> add(registers, registers.c) }
        0x91 -> Instruction("SUB C", 1, 4) { registers -> sub(registers, registers.c) }
        0xA1 -> Instruction("ANA C", 1, 4) { registers -> ana(registers, registers.c) }
        0xB1 -> Instruction("ORA C", 1, 4) { registers -> ora(registers, registers.c) }

        0x82 -> Instruction("ADD D", 1, 4) { registers -> add(registers, registers.d) }
        0x92 -> Instruction("SUB D", 1, 4) { registers -> sub(registers, registers.d) }
        0xA2 -> Instruction("ANA D", 1, 4) { registers -> ana(registers, registers.d) }
        0xB2 -> Instruction("ORA D", 1, 4) { registers -> ora(registers, registers.d) }

        0x83 -> Instruction("ADD E", 1, 4) { registers -> add(registers, registers.e) }
        0x93 -> Instruction("SUB E", 1, 4) { registers -> sub(registers, registers.e) }
        0xA3 -> Instruction("ANA E", 1, 4) { registers -> ana(registers, registers.e) }
        0xB3 -> Instruction("ORA E", 1, 4) { registers -> ora(registers, registers.e) }

        0x84 -> Instruction("ADD H", 1, 4) { registers -> add(registers, registers.h) }
        0x94 -> Instruction("SUB H", 1, 4) { registers -> sub(registers, registers.h) }
        0xA4 -> Instruction("ANA H", 1, 4) { registers -> ana(registers, registers.h) }
        0xB4 -> Instruction("ORA H", 1, 4) { registers -> ora(registers, registers.h) }

        0x85 -> Instruction("ADD L", 1, 4) { registers -> add(registers, registers.l) }
        0x95 -> Instruction("SUB L", 1, 4) { registers -> sub(registers, registers.l) }
        0xA5 -> Instruction("ANA L", 1, 4) { registers -> ana(registers, registers.l) }
        0xB5 -> Instruction("ORA L", 1, 4) { registers -> ora(registers, registers.l) }

        0x86 -> Instruction("ADD M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            add(registers, readByte(address))
        }
        0x96 -> Instruction("SUB M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            sub(registers, readByte(address))
        }
        0xA6 -> Instruction("ANA M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            ana(registers, readByte(address))
        }
        0xB6 -> Instruction("ORA M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            ora(registers, readByte(address))
        }

        0x87 -> Instruction("ADD A", 1, 4) { registers -> add(registers, registers.a) }
        0x97 -> Instruction("SUB A", 1, 4) { registers -> sub(registers, registers.a) }
        0xA7 -> Instruction("ANA A", 1, 4) { registers -> ana(registers, registers.a) }
        0xB7 -> Instruction("ORA A", 1, 4) { registers -> ora(registers, registers.a) }

        0x88 -> Instruction("ADC B", 1, 4) { registers -> adc(registers, registers.b) }
        0x98 -> Instruction("SBB B", 1, 4) { registers -> sbb(registers, registers.b) }
        0xA8 -> Instruction("XRA B", 1, 4) { registers -> xra(registers, registers.b) }
        0xB8 -> Instruction("CMP B", 1, 4) { registers -> cmp(registers, registers.b) }

        0x89 -> Instruction("ADC C", 1, 4) { registers -> adc(registers, registers.c) }
        0x99 -> Instruction("SBB C", 1, 4) { registers -> sbb(registers, registers.c) }
        0xA9 -> Instruction("XRA C", 1, 4) { registers -> xra(registers, registers.c) }
        0xB9 -> Instruction("CMP C", 1, 4) { registers -> cmp(registers, registers.c) }

        0x8A -> Instruction("ADC D", 1, 4) { registers -> adc(registers, registers.d) }
        0x9A -> Instruction("SBB D", 1, 4) { registers -> sbb(registers, registers.d) }
        0xAA -> Instruction("XRA D", 1, 4) { registers -> xra(registers, registers.d) }
        0xBA -> Instruction("CMP D", 1, 4) { registers -> cmp(registers, registers.d) }

        0x8B -> Instruction("ADC E", 1, 4) { registers -> adc(registers, registers.e) }
        0x9B -> Instruction("SBB E", 1, 4) { registers -> sbb(registers, registers.e) }
        0xAB -> Instruction("XRA E", 1, 4) { registers -> xra(registers, registers.e) }
        0xBB -> Instruction("CMP E", 1, 4) { registers -> cmp(registers, registers.e) }

        0x8C -> Instruction("ADC H", 1, 4) { registers -> adc(registers, registers.h) }
        0x9C -> Instruction("SBB H", 1, 4) { registers -> sbb(registers, registers.h) }
        0xAC -> Instruction("XRA H", 1, 4) { registers -> xra(registers, registers.h) }
        0xBC -> Instruction("CMP H", 1, 4) { registers -> cmp(registers, registers.h) }

        0x8D -> Instruction("ADC L", 1, 4) { registers -> adc(registers, registers.l) }
        0x9D -> Instruction("SBB L", 1, 4) { registers -> sbb(registers, registers.l) }
        0xAD -> Instruction("XRA L", 1, 4) { registers -> xra(registers, registers.l) }
        0xBD -> Instruction("CMP L", 1, 4) { registers -> cmp(registers, registers.l) }

        0x8E -> Instruction("ADC M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            adc(registers, readByte(address))
        }
        0x9E -> Instruction("SBB M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            sbb(registers, readByte(address))
        }
        0xAE -> Instruction("XRA M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            xra(registers, readByte(address))
        }
        0xBE -> Instruction("CMP M", 1, 7) { registers ->
            val address = (registers.h shl 8) or registers.l
            cmp(registers, readByte(address))
        }

        0x8F -> Instruction("ADC A", 1, 4) { registers -> adc(registers, registers.a) }
        0x9F -> Instruction("SBB A", 1, 4) { registers -> sbb(registers, registers.a) }
        0xAF -> Instruction("XRA A", 1, 4) { registers -> xra(registers, registers.a) }
        0xBF -> Instruction("CMP A", 1, 4) { registers -> cmp(registers, registers.a) }

        0xC0 -> Instruction("RNZ", 1, 5 + if (!isFlagSet(Flag.Zero)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Zero)) ret(registers)
        }
        0xD0 -> Instruction("RNC", 1, 5 + if (!isFlagSet(Flag.Carry)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Carry)) ret(registers)
        }
        0xE0 -> Instruction("RPO", 1, 5 + if (!isFlagSet(Flag.Parity)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Parity)) ret(registers)
        }
        0xF0 -> Instruction("RP", 1, 5 + if (!isFlagSet(Flag.Sign)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Sign)) ret(registers)
        }

        0xC1 -> Instruction("POP B", 1, 10) { registers ->
            val data = pop(registers)
            registers.b = (data shr 8) and 0xFF
            registers.c = data and 0xFF
        }
        0xD1 -> Instruction("POP D", 1, 10) { registers ->
            val data = pop(registers)
            registers.d = (data shr 8) and 0xFF
            registers.e = data and 0xFF
        }
        0xE1 -> Instruction("POP H", 1, 10) { registers ->
            val data = pop(registers)
            registers.h = (data shr 8) and 0xFF
            registers.l = data and 0xFF
        }
        0xF1 -> Instruction("POP PSW", 1, 10) { registers ->
            val data = pop(registers)
            registers.a = (data shr 8) and 0xFF
            flags = data and 0xFF
        }

        0xC2 -> Instruction("JNZ a16", 3, 10) { registers ->
            if (!isFlagSet(Flag.Zero)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }
        0xD2 -> Instruction("JNC a16", 3, 10) { registers ->
            if (!isFlagSet(Flag.Carry)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }
        0xE2 -> Instruction("JPO a16", 3, 10) { registers ->
            if (!isFlagSet(Flag.Parity)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }
        0xF2 -> Instruction("JP a16", 3, 10) { registers ->
            if (!isFlagSet(Flag.Sign)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }

        0xC3 -> Instruction("JMP a16", 3, 10) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            registers.pc = address and 0xFFFF
        }
        0xD3 -> Instruction("OUT d8", 2, 10) {/* TODO: Порты ввода/вывода */}
        0xE3 -> Instruction("XTHL", 1, 18) { registers ->
            val stackBuffer = pop(registers)
            push(registers, (registers.h shl 8) or registers.l)
            registers.h = (stackBuffer shr 8) and 0xFF
            registers.l = stackBuffer and 0xFF
        }
        0xF3 -> Instruction("DI", 1, 4) {/* TODO: Прерывания */}

        0xC4 -> Instruction("CNZ a16", 3, 11 + if (!isFlagSet(Flag.Zero)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Zero)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }
        0xD4 -> Instruction("CNC a16", 3, 11 + if (!isFlagSet(Flag.Carry)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Carry)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }
        0xE4 -> Instruction("CPO a16", 3, 11 + if (!isFlagSet(Flag.Parity)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Parity)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }
        0xF4 -> Instruction("CP a16", 3, 11 + if (!isFlagSet(Flag.Sign)) 6 else 0) { registers ->
            if (!isFlagSet(Flag.Sign)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }

        0xC5 -> Instruction("PUSH B", 1, 11) { registers ->
            push(registers, (registers.b shl 8) or registers.c)
        }
        0xD5 -> Instruction("PUSH D", 1, 11) { registers ->
            push(registers, (registers.d shl 8) or registers.e)
        }
        0xE5 -> Instruction("PUSH H", 1, 11) { registers ->
            push(registers, (registers.h shl 8) or registers.l)
        }
        0xF5 -> Instruction("PUSH PSW", 1, 11) { registers ->
            push(registers, (registers.a shl 8) or flags)
        }

        0xC6 -> Instruction("ADI d8", 2, 7) {registers ->
            add(registers, readByte(registers.pc + 1))
        }
        0xD6 -> Instruction("SUI d8", 2, 7) { registers ->
            sub(registers, readByte(registers.pc + 1))
        }
        0xE6 -> Instruction("ANI d8", 2, 7) { registers ->
            ana(registers, readByte(registers.pc + 1))
        }
        0xF6 -> Instruction("ORI d8", 2, 7) { registers ->
            ora(registers, readByte(registers.pc + 1))
        }

        0xC7 -> Instruction("RST 0", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x00
        }
        0xD7 -> Instruction("RST 2", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x10
        }
        0xE7 -> Instruction("RST 4", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x20
        }
        0xF7 -> Instruction("RST 6", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x30
        }

        0xC8 -> Instruction("RZ", 1, 5 + if (isFlagSet(Flag.Zero)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Zero)) {
                ret(registers)
            }
        }
        0xD8 -> Instruction("RC", 1, 5 + if (isFlagSet(Flag.Carry)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Carry)) {
                ret(registers)
            }
        }
        0xE8 -> Instruction("RPE", 1, 5 + if (isFlagSet(Flag.Parity)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Parity)) {
                ret(registers)
            }
        }
        0xF8 -> Instruction("RM", 1, 5 + if (isFlagSet(Flag.Sign)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Sign)) {
                ret(registers)
            }
        }

        0xC9 -> Instruction("RET", 1, 10) { registers ->
            ret(registers)
        }
        0xD9 -> Instruction("RET", 1, 10) { registers ->
            ret(registers)
        }
        0xE9 -> Instruction("PCHL", 1, 5) { registers ->
            registers.pc = (registers.h shl 8) or registers.l
        }
        0xF9 -> Instruction("SPHL", 1, 5) { registers ->
            registers.sp = (registers.h shl 8) or registers.l
        }

        0xCA -> Instruction("JZ a16", 3, 10) { registers ->
            if (isFlagSet(Flag.Zero)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }
        0xDA -> Instruction("JC a16", 3, 10) { registers ->
            if (isFlagSet(Flag.Carry)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }
        0xEA -> Instruction("JPE a16", 3, 10) { registers ->
            if (isFlagSet(Flag.Parity)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }
        0xFA -> Instruction("JM a16", 3, 10) { registers ->
            if (isFlagSet(Flag.Sign)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                registers.pc = address and 0xFFFF
            }
        }

        0xCB -> Instruction("JMP a16", 3, 10) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            registers.pc = address and 0xFFFF
        }
        0xDB -> Instruction("IN d8", 2, 10) {/* TODO: Порты ввода/вывода */}
        0xEB -> Instruction("XCHG", 1, 5) { registers ->
            val dBuf = registers.d
            val eBuf = registers.e
            registers.d = registers.h
            registers.e = registers.l
            registers.h = dBuf
            registers.l = eBuf
        }
        0xFB -> Instruction("EI", 1, 4) {/* TODO: Прерывания */}

        0xCC -> Instruction("CZ a16", 3, 11 + if (isFlagSet(Flag.Zero)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Zero)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }
        0xDC -> Instruction("CC a16", 3, 11 + if (isFlagSet(Flag.Carry)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Carry)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }
        0xEC -> Instruction("CPE a16", 3, 11 + if (isFlagSet(Flag.Parity)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Parity)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }
        0xFC -> Instruction("CM a16", 3, 11 + if (isFlagSet(Flag.Sign)) 6 else 0) { registers ->
            if (isFlagSet(Flag.Sign)) {
                val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
                call(registers, address)
            }
        }

        0xCD -> Instruction("CALL a16", 3, 17) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            call(registers, address)
        }
        0xDD -> Instruction("CALL a16", 3, 17) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            call(registers, address)
        }
        0xED -> Instruction("CALL a16", 3, 17) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            call(registers, address)
        }
        0xFD -> Instruction("CALL a16", 3, 17) { registers ->
            val address = (readByte(registers.pc + 2) shl 8) or readByte(registers.pc + 1)
            call(registers, address)
        }

        0xCE -> Instruction("ACI d8", 2, 7) { registers ->
            adc(registers, readByte(registers.pc + 1))
        }
        0xDE -> Instruction("SBI d8", 2, 7) { registers ->
            sbb(registers, readByte(registers.pc + 1))
        }
        0xEE -> Instruction("XRI d8", 2, 7) { registers ->
            xra(registers, readByte(registers.pc + 1))
        }
        0xFE -> Instruction("CPI d8", 2, 7) { registers ->
            cmp(registers, readByte(registers.pc + 1))
        }

        0xCF -> Instruction("RST 1", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x08
        }
        0xDF -> Instruction("RST 3", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x18
        }
        0xEF -> Instruction("RST 5", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x28
        }
        0xFF -> Instruction("RST 7", 1, 11) { registers ->
            resetRegisters(registers)
            registers.pc = 0x38
        }

        else -> throw IllegalArgumentException("Invalid opcode: 0x${opcode.toString(16)}")
    }
}

private fun resetRegisters(registers: Registers) {
    registers.a = 0
    registers.b = 0
    registers.c = 0
    registers.d = 0
    registers.e = 0
    registers.h = 0
    registers.l = 0
    registers.sp = 0
    registers.pc = 0
    flags = 2
}

private fun inr(register: Int): Int {
    val auxCheck = ((register and 0x0F) + 1) shr 4
    val newRegister = (register + 1) and 0xFF
    setFlag(Flag.Sign, (newRegister shr 7) != 0)
    setFlag(Flag.Zero, newRegister == 0)
    setFlag(Flag.AuxiliaryCarry, auxCheck != 0)
    setFlag(Flag.Parity, checkParity(newRegister))
    return newRegister
}

private fun dcr(register: Int): Int {
    val auxCheck = ((register and 0xF0) - 1) shr 4
    val newRegister = (register - 1) and 0xFF
    setFlag(Flag.Sign, (newRegister shr 7) != 0)
    setFlag(Flag.Zero, newRegister == 0)
    setFlag(Flag.AuxiliaryCarry, auxCheck != 0)
    setFlag(Flag.Parity, checkParity(newRegister))
    return newRegister
}

private fun add(registers: Registers, register: Int) {
    val result = registers.a + register

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    setFlag(Flag.AuxiliaryCarry, ((registers.a and 0x0F) + (register and 0x0F)) > 0x0F)
    setFlag(Flag.Parity, checkParity(result))
    setFlag(Flag.Carry, result > 0xFF)

    registers.a = result and 0xFF
}

private fun sub(registers: Registers, register: Int) {
    val result = registers.a - register

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    setFlag(Flag.AuxiliaryCarry, (registers.a and 0x0F) < (register and 0x0F))
    setFlag(Flag.Parity, checkParity(result))
    setFlag(Flag.Carry, result < 0)

    registers.a = result and 0xFF
}

private fun ana(registers: Registers, register: Int) {
    val result = registers.a and register

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    resetFlag(Flag.AuxiliaryCarry)
    setFlag(Flag.Parity, checkParity(result))
    resetFlag(Flag.Carry)

    registers.a = result
}

private fun ora(registers: Registers, register: Int) {
    val result = registers.a or register

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    resetFlag(Flag.AuxiliaryCarry)
    setFlag(Flag.Parity, checkParity(result))
    resetFlag(Flag.Carry)

    registers.a = result
}

private fun adc(registers: Registers, register: Int) {
    val carry = if (isFlagSet(Flag.Carry)) 1 else 0
    val result = registers.a + register + carry

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    setFlag(Flag.AuxiliaryCarry, ((registers.a and 0x0F) + (register and 0x0F) + carry) > 0x0F)
    setFlag(Flag.Parity, checkParity(result))
    setFlag(Flag.Carry, result > 0xFF)

    registers.a = result and 0xFF
}

private fun sbb(registers: Registers, register: Int) {
    val carry = if (isFlagSet(Flag.Carry)) 1 else 0
    val result = registers.a - register - carry

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    setFlag(Flag.AuxiliaryCarry, (registers.a and 0x0F) < ((register and 0x0F) + carry))
    setFlag(Flag.Parity, checkParity(result))
    setFlag(Flag.Carry, result < 0)

    registers.a = result and 0xFF
}

private fun xra(registers: Registers, register: Int) {
    val result = registers.a xor register

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    resetFlag(Flag.AuxiliaryCarry)
    setFlag(Flag.Parity, checkParity(result))
    resetFlag(Flag.Carry)

    registers.a = result
}

private fun cmp(registers: Registers, register: Int) {
    val result = registers.a - register

    setFlag(Flag.Sign, (result shr 7) != 0)
    setFlag(Flag.Zero, result == 0)
    setFlag(Flag.AuxiliaryCarry, (registers.a and 0x0F) < (register and 0x0F))
    setFlag(Flag.Parity, checkParity(result))
    setFlag(Flag.Carry, result < 0)
}

private fun call(registers: Registers, address: Int) {
    push(registers, registers.pc)
    registers.pc = address and 0xFFFF
}
private fun ret(registers: Registers) {
    registers.pc = pop(registers)
}

private fun push(registers: Registers, data: Int) {
    val highCounter = (data shr 8) and 0xFF
    val lowCounter = data and 0xFF
    writeStack(registers.sp, lowCounter)
    writeStack(registers.sp + 1, highCounter)
    registers.sp += 2
}

private fun pop(registers: Registers): Int {
    registers.sp -= 2
    val highCounter = readByte(registers.sp + 1)
    val lowCounter = readByte(registers.sp)
    return (highCounter shl 8) or lowCounter
}

data class Instruction(
    val name: String,
    val length: Int,
    val cycles: Int,
    val execute: (Registers) -> Unit
)

// Instruction executing
fun executeInstruction(instruction: Instruction) {
    instruction.execute(registers)
    registers.pc += instruction.length
    cycleCounter += instruction.cycles
}