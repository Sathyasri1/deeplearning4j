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

package org.deeplearning4j.spark.datavec;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.datavec.api.conf.Configuration;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.impl.misc.SVMLightRecordReader;
import org.deeplearning4j.spark.BaseSparkTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.common.tests.tags.NativeTag;
import org.nd4j.common.tests.tags.TagNames;
import org.nd4j.linalg.dataset.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
@Tag(TagNames.FILE_IO)
@Tag(TagNames.SPARK)
@Tag(TagNames.DIST_SYSTEMS)
@NativeTag
public class MiniBatchTests extends BaseSparkTest {
    private static final Logger log = LoggerFactory.getLogger(MiniBatchTests.class);

    @Test
    public void testMiniBatches() throws Exception {
        log.info("Setting up Spark Context...");
        JavaRDD<String> lines = sc.textFile(new ClassPathResource("svmLight/iris_svmLight_0.txt")
                        .getTempFileFromArchive().toURI().toString()).cache();
        long count = lines.count();
        assertEquals(300, count);
        // gotta map this to a Matrix/INDArray
        RecordReader rr = new SVMLightRecordReader();
        Configuration c = new Configuration();
        c.set(SVMLightRecordReader.NUM_FEATURES, "5");
        rr.setConf(c);
        JavaRDD<DataSet> points = lines.map(new RecordReaderFunction(rr, 4, 3)).cache();
        count = points.count();
        assertEquals(300, count);

        List<DataSet> collect = points.collect();

        points = points.repartition(1);
        JavaRDD<DataSet> miniBatches = new RDDMiniBatches(10, points).miniBatchesJava();
        count = miniBatches.count();
        List<DataSet> list = miniBatches.collect();
        assertEquals(30, count);    //Expect exactly 30 from 1 partition... could be more for multiple input partitions

        lines.unpersist();
        points.unpersist();
        miniBatches.map(new DataSetAssertionFunction());
    }


    public static class DataSetAssertionFunction implements Function<DataSet, Object> {

        @Override
        public Object call(DataSet dataSet) throws Exception {
            assertTrue(dataSet.getFeatures().columns() == 150);
            assertTrue(dataSet.numExamples() == 30);
            return null;
        }
    }


}
