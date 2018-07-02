/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openthos.seafile.seaapp;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;


public class NameFileComparator extends AbstractFileComparator implements Serializable {
    private final IOCase caseSensitivity;

    public static final Comparator<File> NAME_SYSTEM_COMPARATOR = new NameFileComparator(IOCase.SYSTEM);


    public static final Comparator<File> NAME_INSENSITIVE_COMPARATOR = new NameFileComparator(IOCase.INSENSITIVE);

    public static final Comparator<File> NAME_COMPARATOR = new NameFileComparator();

    public NameFileComparator() {
        this.caseSensitivity = IOCase.SENSITIVE;
    }

    public NameFileComparator(final IOCase caseSensitivity) {
        this.caseSensitivity = caseSensitivity == null ? IOCase.SENSITIVE : caseSensitivity;
    }

    @Override
    public int compare(final File file1, final File file2) {
        return caseSensitivity.checkCompareTo(file1.getName(), file2.getName());
    }
}
