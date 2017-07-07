/*
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
package com.facebook.presto.util;

import com.google.common.collect.AbstractIterator;

import java.util.Iterator;

import static java.util.Objects.requireNonNull;

public class PrestoIterators
{
    private PrestoIterators() {}

    public static <T> Iterator<T> runWhenExhausted(Iterator<T> iterator, Runnable onExhaustion)
    {
        requireNonNull(iterator, "iterator is null");
        requireNonNull(onExhaustion, "onExhaustion is null");

        return new AbstractIterator<T>()
        {
            @Override
            protected T computeNext()
            {
                if (iterator.hasNext()) {
                    return iterator.next();
                }
                else {
                    try (UncheckedCloseable ignore = onExhaustion::run) {
                        return endOfData();
                    }
                }
            }
        };
    }

    public static <T> Iterator<T> closeWhenExhausted(Iterator<T> iterator, AutoCloseable resource)
    {
        requireNonNull(resource, "resource is null");

        return runWhenExhausted(iterator, () -> {
            try {
                resource.close();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private interface UncheckedCloseable
            extends AutoCloseable
    {
        @Override
        void close();
    }
}