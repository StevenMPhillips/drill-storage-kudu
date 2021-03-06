/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill.store.kudu;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.kududb.ColumnSchema;
import org.kududb.Schema;
import org.kududb.Type;
import org.kududb.client.CreateTableBuilder;
import org.kududb.client.Insert;
import org.kududb.client.KuduClient;
import org.kududb.client.KuduScanner;
import org.kududb.client.KuduSession;
import org.kududb.client.KuduTable;
import org.kududb.client.ListTablesResponse;
import org.kududb.client.PartialRow;
import org.kududb.client.RowResult;
import org.kududb.client.RowResultIterator;
import org.kududb.client.SessionConfiguration;

import static org.kududb.Type.STRING;


public class TestKuduConnect {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TestKuduConnect.class);

  public static final String KUDU_MASTER = "172.31.1.99";

  public static void createKuduTable(String tableName, int tablets, int replicas, int rows) throws Exception {

    try (KuduClient client = new KuduClient.KuduClientBuilder(KUDU_MASTER).build()) {

      ListTablesResponse tables = client.getTablesList(tableName);
      if (!tables.getTablesList().isEmpty()) {
        client.deleteTable(tableName);
      }

      List<ColumnSchema> columns = new ArrayList<>(5);
      columns.add(new ColumnSchema.ColumnSchemaBuilder("key", Type.INT32).key(true).build());
      columns.add(new ColumnSchema.ColumnSchemaBuilder("binary", Type.BINARY).nullable(false).build());
      columns.add(new ColumnSchema.ColumnSchemaBuilder("boolean", Type.BOOL).nullable(true).build());
      columns.add(new ColumnSchema.ColumnSchemaBuilder("float", Type.FLOAT).nullable(false).build());
      columns.add(new ColumnSchema.ColumnSchemaBuilder("string", Type.STRING).nullable(true).build());

      Schema schema = new Schema(columns);

      CreateTableBuilder builder = new CreateTableBuilder();
      builder.setNumReplicas(replicas);
      for (int i = 1; i < tablets; i++) {
        PartialRow splitRow = schema.newPartialRow();
        splitRow.addInt("key", i*1000);
        builder.addSplitRow(splitRow);
      }

      client.createTable(tableName, schema, builder);

      KuduTable table = client.openTable(tableName);

      KuduSession session = client.newSession();
      session.setFlushMode(SessionConfiguration.FlushMode.AUTO_FLUSH_SYNC);
      for (int i = 0; i < rows; i++) {
        Insert insert = table.newInsert();
        PartialRow row = insert.getRow();
        row.addInt(0, i);
        row.addBinary(1, ("Row " + i).getBytes());
        row.addBoolean(2, i % 2 == 0);
        row.addFloat(3, i + 0.01f);
        row.addString(4, ("Row " + i));
        session.apply(insert);
      }

      List<String> projectColumns = new ArrayList<>(1);
      projectColumns.add("float");
      KuduScanner scanner = client.newScannerBuilder(table)
          .setProjectedColumnNames(projectColumns)
          .build();
      while (scanner.hasMoreRows()) {
        RowResultIterator results = scanner.nextRows();
        while (results.hasNext()) {
          RowResult result = results.next();
          System.out.println(result.toStringLongFormat());
        }
      }
    }
  }

  @Test
  public void abc() throws Exception {
    createKuduTable("demo", 1, 1, 3);
  }

  @Test
  public void def() throws Exception {
    createKuduTable("demo-large-splits", 6, /* replicas */ 1, 6000);
  }
}
