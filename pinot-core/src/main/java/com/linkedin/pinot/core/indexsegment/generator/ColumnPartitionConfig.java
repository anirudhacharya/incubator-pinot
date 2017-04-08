/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.core.indexsegment.generator;

import com.linkedin.pinot.core.data.partition.PartitionFunction;
import com.linkedin.pinot.core.data.partition.PartitionFunctionFactory;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.commons.lang.math.IntRange;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;


@SuppressWarnings("unused") // Suppress incorrect warnings as methods used for ser/de.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColumnPartitionConfig {
  public static final String PARTITION_VALUE_DELIMITER = ",";
  public static final String PARTITIONER_DELIMITER = "\t\t";

  private final String _functionName;

  /**
   * Constructor for the class.
   *
   * @param functionName Name of the partition function, includes delimiter separated arguments. For example:
   *                     "module\t\t20": Function name is module, with argument 20 (divisor).
   */
  public ColumnPartitionConfig(@Nonnull @JsonProperty("functionName") String functionName) {
    _functionName = functionName;
  }

  public String getFunctionName() {
    return _functionName;
  }

  @JsonIgnore
  public PartitionFunction getPartitionFunction() {
    return PartitionFunctionFactory.getPartitionFunction(_functionName);
  }

  /**
   * Helper method to convert an array of ranges in string form (eg [2 3]) into a list
   * of {@link IntRange}. Expects each string range to be formatted correctly.
   *
   * @param inputs Array of ranges in string form.
   * @return List of IntRange's for the given input.
   */
  public static List<IntRange> rangesFromString(String[] inputs) {
    List<IntRange> ranges = new ArrayList<>(inputs.length);
    for (String input : inputs) {

      String trimmed = input.trim();
      String[] split = trimmed.split("\\s+");
      String startString = split[0].substring(1, split[0].length());
      String endString = split[1].substring(0, split[1].length() - 1);
      ranges.add(new IntRange(Integer.parseInt(startString), Integer.parseInt(endString)));
    }
    return ranges;
  }

  /**
   * Helper method to convert a list of {@link IntRange} to a delimited string.
   * The delimiter used is {@link #PARTITION_VALUE_DELIMITER}
   * @param ranges List of ranges to be converted to String.
   * @return String representation of the lis tof ranges.
   */
  public static String rangesToString(List<IntRange> ranges) {
    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < ranges.size(); i++) {
      builder.append("[");
      IntRange range = ranges.get(i);

      builder.append(range.getMinimumInteger());
      builder.append(" ");
      builder.append(range.getMaximumInteger());
      builder.append("]");

      if (i < ranges.size() - 1) {
        builder.append(PARTITION_VALUE_DELIMITER);
      }
    }
    return builder.toString();
  }
}
