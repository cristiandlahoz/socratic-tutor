package com.wornux.infrastructure.external.crunner;

import org.springframework.stereotype.Component;

@Component
class GdbScriptFactory {

    private static final String WORKSPACE = "/workspace";

    String script(int maxSnapshots) {
        var commands = new StringBuilder();
        commands.append("-gdb-set pagination off\n");
        commands.append("-gdb-set print elements 64\n");
        commands.append("-gdb-set step-mode off\n");
        commands.append("-file-exec-and-symbols %s/main\n".formatted(WORKSPACE));
        commands.append("-interpreter-exec console \"break main\"\n");
        commands.append(
            "-interpreter-exec console \"run < %s/stdin.txt > %s/stdout.txt\"\n".formatted(WORKSPACE, WORKSPACE));
        for (int i = 0; i < maxSnapshots; i++) {
            appendSnapshotCommands(commands);
        }
        commands.append("-gdb-exit\n");
        return commands.toString();
    }

    private static void appendSnapshotCommands(StringBuilder commands) {
        commands.append("-stack-list-frames\n");
        commands.append("-interpreter-exec console \"call (int) fflush(0)\"\n");
        commands.append(
            "-interpreter-exec console \"shell printf '__C_STDOUT_BEGIN__\\\\n'; cat %s/stdout.txt 2>/dev/null; printf '__C_STDOUT_END__\\\\n'\"\n"
                    .formatted(WORKSPACE));
        commands.append("-stack-list-variables --all-values\n");
        commands.append("-interpreter-exec console \"step\"\n");
    }
}
