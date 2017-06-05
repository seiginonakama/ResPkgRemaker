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

/**
 * author: zhoulei date: 2017/6/2.
 */
public class ResPkgRemakerExtension {
    private boolean enable = true;
    private int packageId = 0x7f;

    public int getPackageId() {
        return packageId;
    }

    public void setPackageId(int id) {
        if(id < 0x02 || id > 0x7f) {
            throw new IllegalArgumentException("custom package id only can be > 0x01 and <= 0.7f")
        }
        packageId = id;
    }

    public void setEnable(boolean b) {
        enable = b;
    }

    public boolean isEnable() {
        return enable && packageId != 0x7f;
    }
}
