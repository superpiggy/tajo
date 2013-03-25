/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tajo.engine.function.builtin;

import tajo.catalog.Column;
import tajo.catalog.function.FunctionContext;
import tajo.catalog.proto.CatalogProtos;
import tajo.datum.DatumType;
import tajo.storage.Tuple;

public final class CountValue extends CountRows {

  public CountValue() {
    super(new Column[] {
        new Column("col", CatalogProtos.DataType.ANY)
    });
  }
  @Override
  public void eval(FunctionContext ctx, Tuple params) {
    if (params.get(0).type() != DatumType.NULL) {
      ((CountRowContext) ctx).count++;
    }
  }
}