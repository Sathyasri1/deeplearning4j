/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */
package org.datavec.spark.transform;

import org.apache.spark.api.java.JavaRDD;
import org.datavec.api.transform.MathOp;
import org.datavec.api.transform.ReduceOp;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.transform.reduce.Reducer;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.transform.schema.SequenceSchema;
import org.datavec.api.transform.transform.categorical.FirstDigitTransform;
import org.datavec.api.writable.DoubleWritable;
import org.datavec.api.writable.IntWritable;
import org.datavec.api.writable.Text;
import org.datavec.api.writable.Writable;
import org.datavec.spark.BaseSparkTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.tests.tags.TagNames;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Execution Test")
@Tag(TagNames.FILE_IO)
@Tag(TagNames.JAVA_ONLY)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
class ExecutionTest extends BaseSparkTest {

    @Test
    @DisplayName("Test Execution Simple")
    void testExecutionSimple() {
        Schema schema = new Schema.Builder().addColumnInteger("col0").addColumnCategorical("col1", "state0", "state1", "state2").addColumnDouble("col2").build();
        TransformProcess tp = new TransformProcess.Builder(schema).categoricalToInteger("col1").doubleMathOp("col2", MathOp.Add, 10.0).build();
        List<List<Writable>> inputData = new ArrayList<>();
        inputData.add(Arrays.asList(new IntWritable(0), new Text("state2"), new DoubleWritable(0.1)));
        inputData.add(Arrays.asList(new IntWritable(1), new Text("state1"), new DoubleWritable(1.1)));
        inputData.add(Arrays.asList(new IntWritable(2), new Text("state0"), new DoubleWritable(2.1)));
        JavaRDD<List<Writable>> rdd = sc.parallelize(inputData);
        List<List<Writable>> out = new ArrayList<>(SparkTransformExecutor.execute(rdd, tp).collect());
        Collections.sort(out, Comparator.comparingInt(o -> o.get(0).toInt()));
        List<List<Writable>> expected = new ArrayList<>();
        expected.add(Arrays.asList(new IntWritable(0), new IntWritable(2), new DoubleWritable(10.1)));
        expected.add(Arrays.asList(new IntWritable(1), new IntWritable(1), new DoubleWritable(11.1)));
        expected.add(Arrays.asList(new IntWritable(2), new IntWritable(0), new DoubleWritable(12.1)));
        assertEquals(expected, out);
    }

    @Test
    @DisplayName("Test Execution Sequence")
    void testExecutionSequence() {
        Schema schema = new SequenceSchema.Builder().addColumnInteger("col0").addColumnCategorical("col1", "state0", "state1", "state2").addColumnDouble("col2").build();
        TransformProcess tp = new TransformProcess.Builder(schema).categoricalToInteger("col1").doubleMathOp("col2", MathOp.Add, 10.0).build();
        List<List<List<Writable>>> inputSequences = new ArrayList<>();
        List<List<Writable>> seq1 = new ArrayList<>();
        seq1.add(Arrays.asList(new IntWritable(0), new Text("state2"), new DoubleWritable(0.1)));
        seq1.add(Arrays.asList(new IntWritable(1), new Text("state1"), new DoubleWritable(1.1)));
        seq1.add(Arrays.asList(new IntWritable(2), new Text("state0"), new DoubleWritable(2.1)));
        List<List<Writable>> seq2 = new ArrayList<>();
        seq2.add(Arrays.asList(new IntWritable(4), new Text("state1"), new DoubleWritable(4.1)));
        seq2.add(Arrays.asList(new IntWritable(3), new Text("state0"), new DoubleWritable(3.1)));
        inputSequences.add(seq1);
        inputSequences.add(seq2);
        JavaRDD<List<List<Writable>>> rdd = sc.parallelize(inputSequences);
        List<List<List<Writable>>> out = new ArrayList<>(SparkTransformExecutor.executeSequenceToSequence(rdd, tp).collect());
        Collections.sort(out, (o1, o2) -> -Integer.compare(o1.size(), o2.size()));
        List<List<List<Writable>>> expectedSequence = new ArrayList<>();
        List<List<Writable>> seq1e = new ArrayList<>();
        seq1e.add(Arrays.asList(new IntWritable(0), new IntWritable(2), new DoubleWritable(10.1)));
        seq1e.add(Arrays.asList(new IntWritable(1), new IntWritable(1), new DoubleWritable(11.1)));
        seq1e.add(Arrays.asList(new IntWritable(2), new IntWritable(0), new DoubleWritable(12.1)));
        List<List<Writable>> seq2e = new ArrayList<>();
        seq2e.add(Arrays.asList(new IntWritable(4), new IntWritable(1), new DoubleWritable(14.1)));
        seq2e.add(Arrays.asList(new IntWritable(3), new IntWritable(0), new DoubleWritable(13.1)));
        expectedSequence.add(seq1e);
        expectedSequence.add(seq2e);
        assertEquals(expectedSequence, out);
    }

    @Test
    @DisplayName("Test Reduction Global")
    void testReductionGlobal() {
        List<List<Writable>> in = Arrays.asList(Arrays.asList(new Text("first"), new DoubleWritable(3.0)), Arrays.<Writable>asList(new Text("second"), new DoubleWritable(5.0)));
        JavaRDD<List<Writable>> inData = sc.parallelize(in);
        Schema s = new Schema.Builder().addColumnString("textCol").addColumnDouble("doubleCol").build();
        TransformProcess tp = new TransformProcess.Builder(s).reduce(new Reducer.Builder(ReduceOp.TakeFirst).takeFirstColumns("textCol").meanColumns("doubleCol").build()).build();
        JavaRDD<List<Writable>> outRdd = SparkTransformExecutor.execute(inData, tp);
        List<List<Writable>> out = outRdd.collect();
        List<List<Writable>> expOut = Collections.singletonList(Arrays.asList(new Text("first"), new DoubleWritable(4.0)));
        assertEquals(expOut, out);
    }

    @Test
    @DisplayName("Test Reduction By Key")
    void testReductionByKey() {
        List<List<Writable>> in = Arrays.asList(Arrays.asList(new IntWritable(0), new Text("first"), new DoubleWritable(3.0)), Arrays.<Writable>asList(new IntWritable(0), new Text("second"), new DoubleWritable(5.0)), Arrays.<Writable>asList(new IntWritable(1), new Text("f"), new DoubleWritable(30.0)), Arrays.<Writable>asList(new IntWritable(1), new Text("s"), new DoubleWritable(50.0)));
        JavaRDD<List<Writable>> inData = sc.parallelize(in);
        Schema s = new Schema.Builder().addColumnInteger("intCol").addColumnString("textCol").addColumnDouble("doubleCol").build();
        TransformProcess tp = new TransformProcess.Builder(s).reduce(new Reducer.Builder(ReduceOp.TakeFirst).keyColumns("intCol").takeFirstColumns("textCol").meanColumns("doubleCol").build()).build();
        JavaRDD<List<Writable>> outRdd = SparkTransformExecutor.execute(inData, tp);
        List<List<Writable>> out = outRdd.collect();
        List<List<Writable>> expOut = Arrays.asList(Arrays.asList(new IntWritable(0), new Text("first"), new DoubleWritable(4.0)), Arrays.<Writable>asList(new IntWritable(1), new Text("f"), new DoubleWritable(40.0)));
        out = new ArrayList<>(out);
        Collections.sort(out, Comparator.comparingInt(o -> o.get(0).toInt()));
        assertEquals(expOut, out);
    }

    @Test
    @DisplayName("Test Unique Multi Col")
    void testUniqueMultiCol() {
        Schema schema = new Schema.Builder().addColumnInteger("col0").addColumnCategorical("col1", "state0", "state1", "state2").addColumnDouble("col2").build();
        List<List<Writable>> inputData = new ArrayList<>();
        inputData.add(Arrays.asList(new IntWritable(0), new Text("state2"), new DoubleWritable(0.1)));
        inputData.add(Arrays.asList(new IntWritable(1), new Text("state1"), new DoubleWritable(1.1)));
        inputData.add(Arrays.asList(new IntWritable(2), new Text("state0"), new DoubleWritable(2.1)));
        inputData.add(Arrays.asList(new IntWritable(0), new Text("state2"), new DoubleWritable(0.1)));
        inputData.add(Arrays.asList(new IntWritable(1), new Text("state1"), new DoubleWritable(1.1)));
        inputData.add(Arrays.asList(new IntWritable(2), new Text("state0"), new DoubleWritable(2.1)));
        inputData.add(Arrays.asList(new IntWritable(0), new Text("state2"), new DoubleWritable(0.1)));
        inputData.add(Arrays.asList(new IntWritable(1), new Text("state1"), new DoubleWritable(1.1)));
        inputData.add(Arrays.asList(new IntWritable(2), new Text("state0"), new DoubleWritable(2.1)));
        JavaRDD<List<Writable>> rdd = sc.parallelize(inputData);
        Map<String, List<Writable>> l = AnalyzeSpark.getUnique(Arrays.asList("col0", "col1"), schema, rdd);
        assertEquals(2, l.size());
        List<Writable> c0 = l.get("col0");
        assertEquals(3, c0.size());
        assertTrue(c0.contains(new IntWritable(0)) && c0.contains(new IntWritable(1)) && c0.contains(new IntWritable(2)));
        List<Writable> c1 = l.get("col1");
        assertEquals(3, c1.size());
        assertTrue(c1.contains(new Text("state0")) && c1.contains(new Text("state1")) && c1.contains(new Text("state2")));
    }



    @Test
    @DisplayName("Test First Digit Transform Benfords Law")
    void testFirstDigitTransformBenfordsLaw() {
        Schema s = new Schema.Builder().addColumnString("data").addColumnDouble("double").addColumnString("stringNumber").build();
        List<List<Writable>> in = Arrays.asList(Arrays.<Writable>asList(new Text("a"), new DoubleWritable(3.14159), new Text("8e-4")), Arrays.<Writable>asList(new Text("a2"), new DoubleWritable(3.14159), new Text("7e-4")), Arrays.<Writable>asList(new Text("b"), new DoubleWritable(2.71828), new Text("7e2")), Arrays.<Writable>asList(new Text("c"), new DoubleWritable(1.61803), new Text("6e8")), Arrays.<Writable>asList(new Text("c"), new DoubleWritable(1.61803), new Text("2.0")), Arrays.<Writable>asList(new Text("c"), new DoubleWritable(1.61803), new Text("2.1")), Arrays.<Writable>asList(new Text("c"), new DoubleWritable(1.61803), new Text("2.2")), Arrays.<Writable>asList(new Text("c"), new DoubleWritable(-2), new Text("non numerical")));
        // Test Benfords law use case:
        TransformProcess tp = new TransformProcess.Builder(s).firstDigitTransform("double", "fdDouble", FirstDigitTransform.Mode.EXCEPTION_ON_INVALID).firstDigitTransform("stringNumber", "stringNumber", FirstDigitTransform.Mode.INCLUDE_OTHER_CATEGORY).removeAllColumnsExceptFor("stringNumber").categoricalToOneHot("stringNumber").reduce(new Reducer.Builder(ReduceOp.Sum).build()).build();
        JavaRDD<List<Writable>> rdd = sc.parallelize(in);
        List<List<Writable>> out = SparkTransformExecutor.execute(rdd, tp).collect();
        assertEquals(1, out.size());
        List<Writable> l = out.get(0);
        List<Writable> exp = Arrays.asList(// 0
        new IntWritable(0), // 1
        new IntWritable(0), // 2
        new IntWritable(3), // 3
        new IntWritable(0), // 4
        new IntWritable(0), // 5
        new IntWritable(0), // 6
        new IntWritable(1), // 7
        new IntWritable(2), // 8
        new IntWritable(1), // 9
        new IntWritable(0), // Other
        new IntWritable(1));
        assertEquals(exp, l);
    }
}
