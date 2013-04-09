/*
 * DecompilerHelpers.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is based on Mono.Cecil from Jb Evain, Copyright (c) Jb Evain;
 * and ILSpy/ICSharpCode from SharpDevelop, Copyright (c) AlphaSierraPapa.
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.decompiler;

import com.strobel.assembler.ir.ExceptionBlock;
import com.strobel.assembler.ir.ExceptionHandler;
import com.strobel.assembler.ir.Frame;
import com.strobel.assembler.ir.FrameType;
import com.strobel.assembler.ir.FrameValue;
import com.strobel.assembler.ir.FrameValueType;
import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.metadata.*;
import com.strobel.core.StringUtilities;
import com.strobel.core.VerifyArgument;
import com.strobel.decompiler.ast.Variable;
import com.strobel.decompiler.languages.java.JavaOutputVisitor;

import java.util.List;
import java.util.Stack;

import static java.lang.String.format;

public final class DecompilerHelpers {
    public static void writeType(final ITextOutput writer, final TypeReference type) {
        writeType(writer, type, NameSyntax.SIGNATURE);
    }

    public static void writeGenericSignature(final ITextOutput writer, final TypeReference type) {
        formatGenericSignature(writer, type, new Stack<TypeReference>());
    }

    public static void writeType(final ITextOutput writer, final TypeReference type, final NameSyntax syntax) {
        VerifyArgument.notNull(type, "type");
        VerifyArgument.notNull(writer, "writer");
        VerifyArgument.notNull(syntax, "syntax");

        formatType(writer, type, syntax, type.isDefinition(), new Stack<TypeReference>());
    }

    public static void writeMethod(final ITextOutput writer, final MethodReference method) {
        VerifyArgument.notNull(method, "method");
        VerifyArgument.notNull(writer, "writer");

        final Stack<TypeReference> typeStack = new Stack<>();

        formatType(writer, method.getDeclaringType(), NameSyntax.DESCRIPTOR, false, typeStack);
        writer.writeDelimiter(".");
        writer.writeReference(method.getName(), method);
        writer.writeDelimiter(":");
        formatMethodSignature(writer, method, typeStack);
    }

    public static void writeMethodSignature(final ITextOutput writer, final IMethodSignature signature) {
        VerifyArgument.notNull(signature, "signature");
        VerifyArgument.notNull(writer, "writer");

        final Stack<TypeReference> typeStack = new Stack<>();

        formatMethodSignature(writer, signature, typeStack);
    }

    public static void writeField(final ITextOutput writer, final FieldReference field) {
        VerifyArgument.notNull(field, "field");
        VerifyArgument.notNull(writer, "writer");

        final Stack<TypeReference> typeStack = new Stack<>();

        formatType(writer, field.getDeclaringType(), NameSyntax.DESCRIPTOR, false, typeStack);
        writer.writeDelimiter(".");
        writer.writeReference(field.getFullName(), field);
        writer.writeDelimiter(":");
        formatType(writer, field.getFieldType(), NameSyntax.SIGNATURE, false, typeStack);
    }

    public static void writeOperand(final ITextOutput writer, final Object operand) {
        VerifyArgument.notNull(writer, "writer");
        VerifyArgument.notNull(operand, "operand");

        if (operand instanceof Instruction) {
            final Instruction targetInstruction = (Instruction) operand;
            writeOffsetReference(writer, targetInstruction);
            return;
        }

        if (operand instanceof Instruction[]) {
            final Instruction[] targetInstructions = (Instruction[]) operand;
            writeLabelList(writer, targetInstructions);
            return;
        }

        if (operand instanceof VariableReference) {
            final VariableReference variable = (VariableReference) operand;

            if (variable.hasName()) {
                writer.writeReference(escapeIdentifier(variable.getName()), variable);
            }
            else {
                writer.writeReference("$" + String.valueOf(variable.getSlot()), variable);
            }

            return;
        }

        if (operand instanceof ParameterReference) {
            final ParameterReference parameter = (ParameterReference) operand;
            final String parameterName = parameter.getName();

            if (StringUtilities.isNullOrEmpty(parameterName)) {
                writer.writeReference(String.valueOf(parameter.getPosition()), parameter);
            }
            else {
                writer.writeReference(escapeIdentifier(parameterName), parameter);
            }

            return;
        }

        if (operand instanceof Variable) {
            final Variable variable = (Variable) operand;

            if (variable.isParameter()) {
                writer.writeReference(variable.getName(), variable.getOriginalParameter());
            }
            else {
                writer.writeReference(variable.getName(), variable.getOriginalVariable());
            }

            return;
        }

        if (operand instanceof MethodReference) {
            writeMethod(writer, (MethodReference) operand);
            return;
        }

        if (operand instanceof TypeReference) {
            writeType(writer, (TypeReference) operand, NameSyntax.TYPE_NAME);
            writer.write('.');
            writer.writeKeyword("class");
            return;
        }

        if (operand instanceof FieldReference) {
            writeField(writer, (FieldReference) operand);
            return;
        }

        if (operand instanceof String) {
            writer.writeLiteral(JavaOutputVisitor.convertString((String) operand, true));
            return;
        }

        if (operand instanceof Character) {
            writer.writeLiteral(String.valueOf((int) ((Character) operand).charValue()));
            return;
        }

        if (operand instanceof Boolean) {
            writer.writeKeyword(Boolean.TRUE.equals(operand) ? "true" : "false");
            return;
        }

        if (operand instanceof Number) {
            writer.writeLiteral(operand);
            return;
        }

        writer.write(String.valueOf(operand));
    }

    public static String offsetToString(final int offset) {
        return format("#%1$04d", offset);
    }

    public static void writeExceptionHandler(final ITextOutput output, final ExceptionHandler handler) {
        VerifyArgument.notNull(output, "output");
        VerifyArgument.notNull(handler, "handler");

        output.write("Try ");
        writeOffsetReference(output, handler.getTryBlock().getFirstInstruction());
        output.write(" - ");
        writeEndOffsetReference(output, handler.getTryBlock().getLastInstruction());
        output.write(' ');
        output.write(String.valueOf(handler.getHandlerType()));

        final TypeReference catchType = handler.getCatchType();

        if (catchType != null) {
            output.write(' ');
            writeType(output, catchType);
        }

        final ExceptionBlock handlerBlock = handler.getHandlerBlock();

        output.write(' ');
        writeOffsetReference(output, handlerBlock.getFirstInstruction());

        if (handlerBlock.getLastInstruction() != null) {
            output.write(" - ");
            writeEndOffsetReference(output, handlerBlock.getLastInstruction());
        }
    }

    public static void writeInstruction(final ITextOutput writer, final Instruction instruction) {
        VerifyArgument.notNull(writer, "writer");
        VerifyArgument.notNull(instruction, "instruction");

        writer.writeDefinition(offsetToString(instruction.getOffset()), instruction);
        writer.write(": ");
        writer.writeReference(instruction.getOpCode().name(), instruction.getOpCode());

        if (instruction.hasOperand()) {
            writer.write(' ');
            writeOperandList(writer, instruction);
        }
    }

    public static void writeOffsetReference(final ITextOutput writer, final Instruction instruction) {
        VerifyArgument.notNull(writer, "writer");

        writer.writeReference(offsetToString(instruction.getOffset()), instruction);
    }

    public static void writeEndOffsetReference(final ITextOutput writer, final Instruction instruction) {
        VerifyArgument.notNull(writer, "writer");

        writer.writeReference(offsetToString(instruction.getEndOffset()), instruction);
    }

    public static String escapeIdentifier(final String name) {
        VerifyArgument.notNull(name, "name");

        StringBuilder sb = null;

        for (int i = 0, n = name.length(); i < n; i++) {
            final char ch = name.charAt(i);

            if (i == 0) {
                if (Character.isJavaIdentifierStart(ch)) {
                    continue;
                }
                sb = new StringBuilder(name.length() * 2);
                sb.append(format("\\u%1$04x", (int) ch));
            }
            else if (Character.isJavaIdentifierPart(ch)) {
                if (sb != null) {
                    sb.append(ch);
                }
            }
            else {
                if (sb == null) {
                    sb = new StringBuilder(name.length() * 2);
                }
                sb.append(format("\\u%1$04x", (int) ch));
            }
        }

        if (sb != null) {
            return sb.toString();
        }

        return name;
    }

    public static void writeFrame(final ITextOutput writer, final Frame frame) {
        VerifyArgument.notNull(writer, "writer");
        VerifyArgument.notNull(frame, "frame");

        final FrameType frameType = frame.getFrameType();

        writer.write(String.valueOf(frameType));

        final List<FrameValue> localValues = frame.getLocalValues();
        final List<FrameValue> stackValues = frame.getStackValues();

        if (!localValues.isEmpty()) {
            writer.writeLine();
            writer.indent();
            writer.write("Locals: [");

            for (int i = 0; i < localValues.size(); i++) {
                final FrameValue value = localValues.get(i);

                if (i != 0) {
                    writer.write(", ");
                }

                if (value.getType() == FrameValueType.Reference) {
                    writer.write("Ref(");
                    writeType(writer, (TypeReference) value.getParameter(), NameSyntax.SIGNATURE);
                    writer.write(')');
                }
                else {
                    writer.write(String.valueOf(value.getType()));
                }
            }

            writer.write("]");
            writer.unindent();
        }

        if (!stackValues.isEmpty()) {
            writer.writeLine();
            writer.indent();
            writer.write("Stack: [");

            for (int i = 0; i < stackValues.size(); i++) {
                final FrameValue value = stackValues.get(i);

                if (i != 0) {
                    writer.write(", ");
                }

                if (value.getType() == FrameValueType.Reference) {
                    writeType(writer, (TypeReference) value.getParameter(), NameSyntax.SIGNATURE);
                }
                else {
                    writer.write(String.valueOf(value.getType()));
                }
            }

            writer.write("]");
            writer.unindent();
        }
    }

    private static void writeLabelList(final ITextOutput writer, final Instruction[] instructions) {
        writer.write('(');

        for (int i = 0; i < instructions.length; i++) {
            if (i != 0) {
                writer.write(", ");
            }
            writeOffsetReference(writer, instructions[i]);
        }

        writer.write(')');
    }

    private static void writeOperandList(final ITextOutput writer, final Instruction instruction) {
        for (int i = 0, n = instruction.getOperandCount(); i < n; i++) {
            if (i != 0) {
                writer.write(", ");
            }
            writeOperand(writer, instruction.getOperand(i));
        }
    }

    private static void formatMethodSignature(
        final ITextOutput writer,
        final IMethodSignature signature,
        final Stack<TypeReference> typeStack) {

        if (signature.isGenericDefinition()) {
            final List<GenericParameter> genericParameters = signature.getGenericParameters();
            final int count = genericParameters.size();

            if (count > 0) {
                writer.writeDelimiter("<");
                for (int i = 0; i < count; ++i) {
                    formatGenericSignature(writer, genericParameters.get(i), typeStack);
                }
                writer.writeDelimiter(">");
            }
        }

        final List<ParameterDefinition> parameters = signature.getParameters();

        writer.writeDelimiter("(");

        for (int i = 0, n = parameters.size(); i < n; ++i) {
            final ParameterDefinition p = parameters.get(i);
            formatType(writer, p.getParameterType(), NameSyntax.SIGNATURE, false, typeStack);
        }

        writer.writeDelimiter(")");

        formatType(writer, signature.getReturnType(), NameSyntax.SIGNATURE, false, typeStack);
    }

    @SuppressWarnings("ConstantConditions")
    private static void formatType(
        final ITextOutput writer,
        final TypeReference type,
        final NameSyntax syntax,
        final boolean isDefinition,
        final Stack<TypeReference> stack) {

        if (type.isGenericParameter()) {
            switch (syntax) {
                case SIGNATURE:
                case ERASED_SIGNATURE: {
                    formatType(writer, type.getExtendsBound(), syntax, isDefinition, stack);
                    return;
                }

                case DESCRIPTOR: {
                    writer.writeReference("T" + type.getName() + ";", type);
                    return;
                }

                default: {
                    writer.writeReference(type.getName(), type);

                    if (type.hasExtendsBound() && !stack.contains(type.getExtendsBound())) {
                        writer.writeKeyword(" extends ");
                        stack.push(type);
                        try {
                            formatType(writer, type.getExtendsBound(), syntax, false, stack);
                        }
                        finally {
                            stack.pop();
                        }
                    }

                    return;
                }
            }
        }

        if (type.isWildcardType()) {
            switch (syntax) {
                case DESCRIPTOR: {
                    formatType(writer, type.getExtendsBound(), syntax, false, stack);
                    return;
                }

                case SIGNATURE:
                case ERASED_SIGNATURE: {
                    if (type.hasSuperBound()) {
                        writer.write("-");
                        formatType(writer, type.getSuperBound(), syntax, false, stack);
                    }
                    else if (type.hasExtendsBound()) {
                        writer.write("+");
                        formatType(writer, type.getExtendsBound(), syntax, false, stack);
                    }
                    else {
                        writer.write("*");
                    }
                    return;
                }

                default: {
                    writer.write("?");

                    if (type.hasSuperBound()) {
                        writer.writeKeyword(" super ");
                        formatType(writer, type.getSuperBound(), syntax, false, stack);
                    }
                    else if (type.hasExtendsBound()) {
                        writer.writeKeyword(" extends ");
                        formatType(writer, type.getExtendsBound(), syntax, false, stack);
                    }

                    return;
                }
            }
        }

        if (type.isArray()) {
            switch (syntax) {
                case SIGNATURE:
                case ERASED_SIGNATURE:
                case DESCRIPTOR: {
                    writer.write("[");
                    formatType(writer, type.getElementType(), syntax, false, stack);
                    break;
                }

                case TYPE_NAME:
                case SHORT_TYPE_NAME: {
                    formatType(writer, type.getElementType(), syntax, false, stack);
                    writer.write("[]");
                }
            }

            return;
        }

        stack.push(type);

        final TypeDefinition resolvedType = type.resolve();
        final TypeReference nameSource =  resolvedType != null ? resolvedType : type;

        try {
            final String name;

            switch (syntax) {
                case TYPE_NAME:
                    name = nameSource.getFullName();
                    break;
                case SHORT_TYPE_NAME:
                    name = nameSource.getSimpleName();
                    break;
                case DESCRIPTOR:
                    name = nameSource.getInternalName();
                    break;
                default:
                    if (nameSource.isPrimitive()) {
                        name = nameSource.getInternalName();
                    }
                    else {
                        writer.write('L');
                        name = nameSource.getInternalName();
                    }
                    break;
            }

            if (type.isPrimitive() && (syntax == NameSyntax.TYPE_NAME || syntax == NameSyntax.SHORT_TYPE_NAME)) {
                writer.writeKeyword(name);
            }
            else if (isDefinition) {
                writer.writeDefinition(name, type);
            }
            else {
                writer.writeReference(name, type);
            }

            if (type instanceof IGenericInstance &&
                syntax != NameSyntax.DESCRIPTOR &&
                syntax != NameSyntax.ERASED_SIGNATURE) {

                final List<TypeReference> typeArguments = ((IGenericInstance) type).getTypeArguments();
                final int count = typeArguments.size();

                if (count > 0) {
                    writer.writeDelimiter("<");
                    for (int i = 0; i < count; ++i) {
                        final TypeReference typeArgument = typeArguments.get(i);

                        formatType(writer, typeArgument, syntax, false, stack);
                    }
                    writer.writeDelimiter(">");
                }
            }

            if (!type.isPrimitive() && (syntax == NameSyntax.SIGNATURE || syntax == NameSyntax.ERASED_SIGNATURE)) {
                writer.write(';');
            }
        }
        finally {
            stack.pop();
        }
    }

    private static void formatGenericSignature(
        final ITextOutput writer,
        final TypeReference type,
        final Stack<TypeReference> stack) {

        if (type.isGenericParameter()) {
            final TypeReference extendsBound = type.getExtendsBound();
            final TypeDefinition resolvedBound = extendsBound.resolve();

            writer.write(type.getName());

            if (resolvedBound != null && resolvedBound.isInterface()) {
                writer.writeDelimiter(":");
            }

            writer.writeDelimiter(":");

            formatType(writer, extendsBound, NameSyntax.SIGNATURE, false, stack);

            return;
        }

        if (type instanceof IGenericInstance) {
            final List<TypeReference> typeArguments = ((IGenericInstance) type).getTypeArguments();
            final int count = typeArguments.size();

            if (count > 0) {
                writer.writeDelimiter("<");
                //noinspection ForLoopReplaceableByForEach
                for (int i = 0; i < count; ++i) {
                    formatGenericSignature(writer, typeArguments.get(i), stack);
                }
                writer.writeDelimiter(">");
            }
        }

        final TypeDefinition definition = type.resolve();

        if (definition == null) {
            return;
        }

        final TypeReference baseType = definition.getBaseType();
        final List<TypeReference> interfaces = definition.getExplicitInterfaces();

        if (baseType == null) {
            if (interfaces.isEmpty()) {
                formatType(writer, BuiltinTypes.Object, NameSyntax.SIGNATURE, false, stack);
            }
        }
        else {
            formatType(writer, baseType, NameSyntax.SIGNATURE, false, stack);
        }

        for (final TypeReference interfaceType : interfaces) {
            formatType(writer, interfaceType, NameSyntax.SIGNATURE, false, stack);
        }
    }
}