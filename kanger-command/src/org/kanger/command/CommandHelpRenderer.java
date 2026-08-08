/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import java.util.LinkedHashMap;
import java.util.Map;

/** Renders canonical command help directly from {@link CommandRegistry}. */
public final class CommandHelpRenderer {

    public String render() {
        StringBuilder out = new StringBuilder();
        String currentSection = null;

        for (CommandRegistry.Definition definition : CommandRegistry.definitions()) {
            if (!definition.getHelpSection().equals(currentSection)) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                currentSection = definition.getHelpSection();
                out.append(currentSection).append(':').append('\n');
            }
            out.append("  ")
                    .append(definition.getSyntax())
                    .append('\n')
                    .append("      ")
                    .append(definition.getSummary())
                    .append('\n');
            for (Map.Entry<String, String> argument
                    : definition.getArgumentDescriptions().entrySet()) {
                out.append("      ")
                        .append('<').append(argument.getKey()).append('>')
                        .append(" — ")
                        .append(argument.getValue())
                        .append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Returns registry definitions grouped in display order for UI discovery
     * without forcing a textual rendering.
     */
    public Map<String, java.util.List<CommandRegistry.Definition>> sections() {
        Map<String, java.util.List<CommandRegistry.Definition>> result =
                new LinkedHashMap<String, java.util.List<CommandRegistry.Definition>>();
        for (CommandRegistry.Definition definition : CommandRegistry.definitions()) {
            java.util.List<CommandRegistry.Definition> section =
                    result.get(definition.getHelpSection());
            if (section == null) {
                section = new java.util.ArrayList<CommandRegistry.Definition>();
                result.put(definition.getHelpSection(), section);
            }
            section.add(definition);
        }
        return result;
    }
}
