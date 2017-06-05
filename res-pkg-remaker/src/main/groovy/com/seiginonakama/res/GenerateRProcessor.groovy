/*
 * Copyright (C) 2017 seiginonakama (https://github.com/seiginonakama).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.seiginonakama.res;

import java.util.regex.Matcher
import java.util.regex.Pattern;

/**
 * replace all 0x7f -> 0x{customPackageId} in R.java and R.txt
 *
 * author: zhoulei date: 2017/6/2.
 */
public class GenerateRProcessor {
    private static final Pattern RID_0x7f = Pattern.compile("0x7f[0-9a-fA-F]{6}")
    private final int customPackageId;

    public GenerateRProcessor(int pkgId) {
        customPackageId = pkgId;
    }

    public void process(File file) {
        File newFile = new File(file.absolutePath + '.tmp')
        BufferedWriter writer = newFile.newWriter(false);
        List<String> lines = file.readLines();
        for (String line : lines) {
            Matcher matcher = RID_0x7f.matcher(line)
            while (matcher.find()) {
                String match = matcher.group();
                line = line.replaceAll(match, match.replaceAll("0x7f", "0x${Integer.toHexString(customPackageId)}"))
            }
            writer.writeLine(line)
        }
        file.delete()
        writer.flush()
        writer.close()
        newFile.renameTo(file)
    }
}
