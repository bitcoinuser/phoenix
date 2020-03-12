/*
 * Copyright 2019 ACINQ SAS
 *
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

package fr.acinq.phoenix.utils

class TorSetupException(val s: String) : Exception(s)
class NetworkException : RuntimeException()
class KitNotInitialized : RuntimeException("kit is not initialized")
class InsufficientBalance : RuntimeException()
class SwapOutInsufficientAmount : RuntimeException()
class BitcoinURIParseException : Exception {
  constructor(s: String) : super(s)

  constructor(s: String, throwable: Throwable) : super(s, throwable)
}
