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

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.google.common.net.HostAndPort
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.Block
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.DeterministicWallet
import fr.acinq.eclair.blockchain.singleaddress.SingleAddressEclairWallet
import fr.acinq.eclair.io.NodeURI
import fr.acinq.eclair.payment.PaymentRequest
import fr.acinq.phoenix.BuildConfig
import fr.acinq.phoenix.Xpub
import fr.acinq.phoenix.lnurl.LNUrl
import fr.acinq.phoenix.utils.tor.TorHelper
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object Wallet {

  val log: Logger = LoggerFactory.getLogger(this::class.java)

  val ACINQ: NodeURI = NodeURI.parse("03933884aaf1d6b108397e5efe5c86bcf2d8ca8d2f700eda99db9214fc2712b134@endurance.acinq.co:9735")
  val httpClient = OkHttpClient()

  // ------------------------ DATADIR & FILES

  private const val ECLAIR_BASE_DATADIR = "node-data"
  private const val SEED_FILE = "seed.dat"
  private const val ECLAIR_DB_FILE = "eclair.sqlite"
  private const val NETWORK_DB_FILE = "network.sqlite"
  private const val WALLET_DB_FILE = "wallet.sqlite"

  fun getDatadir(context: Context): File {
    return File(context.filesDir, ECLAIR_BASE_DATADIR)
  }

  fun getSeedFile(context: Context): File {
    return File(getDatadir(context), SEED_FILE)
  }

  fun getChainDatadir(context: Context): File {
    return File(getDatadir(context), BuildConfig.CHAIN)
  }

  fun getEclairDBFile(context: Context): File {
    return File(getChainDatadir(context), ECLAIR_DB_FILE)
  }

  fun getNetworkDBFile(context: Context): File {
    return File(getChainDatadir(context), NETWORK_DB_FILE)
  }

  fun isMainnet(): Boolean = "mainnet" == BuildConfig.CHAIN

  fun getChainHash(): ByteVector32 {
    return if (isMainnet()) Block.LivenetGenesisBlock().hash() else Block.TestnetGenesisBlock().hash()
  }

  fun buildAddress(master: DeterministicWallet.ExtendedPrivateKey): SingleAddressEclairWallet {
    val path = if (isMainnet()) {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/0'/0'/0/0")
    } else {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/1'/0'/0/0")
    }
    return SingleAddressEclairWallet(fr.acinq.bitcoin.`package$`.`MODULE$`.computeBIP84Address(DeterministicWallet.derivePrivateKey(master, path).publicKey(), getChainHash()))
  }

  fun buildXpub(master: DeterministicWallet.ExtendedPrivateKey): Xpub {
    val path = if (isMainnet()) {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/0'/0'")
    } else {
      DeterministicWallet.`KeyPath$`.`MODULE$`.apply("m/84'/1'/0'")
    }
    val xpub = DeterministicWallet.encode(DeterministicWallet.publicKey(DeterministicWallet.derivePrivateKey(master, path)),
      if (isMainnet()) DeterministicWallet.zpub() else DeterministicWallet.vpub())
    return Xpub(xpub = xpub, path = path.toString())
  }

  private fun cleanUpInvoice(input: String): String {
    val trimmed = input.replace("\\u00A0", "").trim()
    return when {
      trimmed.startsWith("lightning://", true) -> trimmed.drop(12)
      trimmed.startsWith("lightning:", true) -> trimmed.drop(10)
      trimmed.startsWith("bitcoin://", true) -> trimmed.drop(10)
      trimmed.startsWith("bitcoin:", true) -> trimmed.drop(8)
      else -> trimmed
    }
  }

  /**
   * This methods reads arbitrary strings and deserializes it to a Lightning/Bitcoin object, if possible.
   * <p>
   * Object can be of type:
   * - Lightning payment request (BOLT 11)
   * - BitcoinURI
   * - LNURL
   *
   * If the string cannot be read, throws an exception.
   */
  fun parseLNObject(input: String): Any {
    val invoice = cleanUpInvoice(input)
    return try {
      PaymentRequest.read(invoice)
    } catch (e1: Exception) {
      try {
        BitcoinURI(invoice)
      } catch (e2: Exception) {
        try {
          // this can take some time since a HTTP request may be done
          LNUrl.extractLNUrl(invoice)
        } catch (e3: Exception) {
          log.debug("unhandled input=$input")
          log.debug("invalid as PaymentRequest: ${e1.localizedMessage}")
          log.debug("invalid as BitcoinURI: ${e2.localizedMessage}")
          log.debug("invalid as LNURL: ", e3)
          throw UnreadableLightningObject("not a readable lightning object: ${e1.localizedMessage} / ${e2.localizedMessage} / ${e3.localizedMessage}")
        }
      }
    }
  }

  fun showKeyboard(context: Context?, view: View) {
    context?.let {
      val imm = it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.showSoftInput(view, InputMethodManager.RESULT_UNCHANGED_SHOWN)
    }
  }

  fun hideKeyboard(context: Context?, view: View) {
    context?.let {
      val imm = it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
  }

  /**
   * Builds a TypeSafe configuration for the node. Returns an empty config if there are no valid user's prefs.
   */
  fun getOverrideConfig(context: Context): Config {
    val conf = HashMap<String, Any>()

    // electrum config
    val electrumServer = Prefs.getElectrumServer(context)
    if (electrumServer.isNotBlank()) {
      try {
        val address = HostAndPort.fromString(electrumServer).withDefaultPort(50002)
        if (address.host.isNotBlank()) {
          conf["eclair.electrum.host"] = address.host
          conf["eclair.electrum.port"] = address.port
          if (address.isOnion()) {
            // If Tor is used, we don't require TLS; Tor already adds a layer of encryption.
            // However the user can still force the app to check the certificate.
            conf["eclair.electrum.ssl"] = if (Prefs.getForceElectrumSSL(context)) "strict" else "off"
          } else {
            // Otherwise we require TLS with a valid server certificate.
            conf["eclair.electrum.ssl"] = "strict"
          }
        }
      } catch (e: Exception) {
        throw InvalidElectrumAddress(electrumServer)
      }
    }

    // TOR config
    if (Prefs.isTorEnabled(context)) {
      conf["eclair.socks5.enabled"] = true
      conf["eclair.socks5.host"] = "127.0.0.1"
      conf["eclair.socks5.port"] = TorHelper.PORT
      conf["eclair.socks5.use-for-ipv4"] = true
      conf["eclair.socks5.use-for-ipv6"] = true
      conf["eclair.socks5.use-for-tor"] = true
      conf["eclair.socks5.randomize-credentials"] = false // this allows tor stream isolation
    }

    return ConfigFactory.parseMap(conf)
  }

  fun HostAndPort.isOnion() = this.host.endsWith(".onion")
}
