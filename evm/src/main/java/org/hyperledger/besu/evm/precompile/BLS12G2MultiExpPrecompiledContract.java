/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.nativelib.bls12_381.LibEthPairings;

import org.apache.tuweni.bytes.Bytes;

public class BLS12G2MultiExpPrecompiledContract extends AbstractBLS12PrecompiledContract {

  public BLS12G2MultiExpPrecompiledContract() {
    super("BLS12_G2MULTIEXP", LibEthPairings.BLS12_G2MULTIEXP_OPERATION_RAW_VALUE);
  }

  @Override
  public Gas gasRequirement(final Bytes input) {
    final int k = input.size() / 288;
    return Gas.of(55L * k * getDiscount(k));
  }
}
