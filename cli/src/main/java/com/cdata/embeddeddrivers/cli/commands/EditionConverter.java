package com.cdata.embeddeddrivers.cli.commands;

import com.cdata.embeddeddrivers.core.Edition;

import picocli.CommandLine.ITypeConverter;

/** Lenient picocli converter for Edition values (case- and separator-insensitive). */
public class EditionConverter implements ITypeConverter<Edition> {

    @Override
    public Edition convert(String value) {
        return Edition.parse(value);
    }
}
