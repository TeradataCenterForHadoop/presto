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
package com.facebook.presto.hive.parquet.reader;

import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.block.BlockBuilderStatus;
import com.facebook.presto.spi.type.DecimalType;
import com.facebook.presto.spi.type.LongDecimalType;
import parquet.column.ColumnDescriptor;
import parquet.io.api.Binary;

import java.math.BigInteger;

public class ParquetLongDecimalColumnReader
        extends ParquetColumnReader
{
    private final DecimalType decimalType;

    ParquetLongDecimalColumnReader(ColumnDescriptor descriptor, DecimalType decimalType)
    {
        super(descriptor);
        this.decimalType = decimalType;
    }

    public BlockBuilder createBlockBuilder()
    {
        return decimalType.createBlockBuilder(new BlockBuilderStatus(), nextBatchSize);
    }

    @Override
    public void readValues(BlockBuilder blockBuilder, int valueNumber)
    {
        for (int i = 0; i < valueNumber; i++) {
            if (definitionReader.readLevel() == columnDescriptor.getMaxDefinitionLevel()) {
                Binary value = valuesReader.readBytes();
                decimalType.writeSlice(blockBuilder, LongDecimalType.unscaledValueToSlice(new BigInteger(value.getBytes())));
            }
            else {
                blockBuilder.appendNull();
            }
        }
    }

    @Override
    public void skipValues(int offsetNumber)
    {
        for (int i = 0; i < offsetNumber; i++) {
            if (definitionReader.readLevel() == columnDescriptor.getMaxDefinitionLevel()) {
                valuesReader.readBytes();
            }
        }
    }
}
