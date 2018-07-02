/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

import java.util.AbstractList;
import java.util.RandomAccess;

/**
 * An indexed set of values that may be read with {@link BufferedSource#select}.
 */
public final class Options extends AbstractList<ByteString> implements RandomAccess {
    final ByteString[] byteStrings;

    private Options(ByteString[] byteStrings) {
        this.byteStrings = byteStrings;
    }

    public static Options of(ByteString... byteStrings) {
        return new Options(byteStrings.clone()); // Defensive copy.
    }

    @Override
    public ByteString get(int i) {
        return byteStrings[i];
    }

    @Override
    public final int size() {
        return byteStrings.length;
    }
}
