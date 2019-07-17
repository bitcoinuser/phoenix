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

package fr.acinq.eclair.phoenix

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import fr.acinq.eclair.phoenix.security.PinDialog
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class BaseFragment : Fragment() {

  open val log: Logger = LoggerFactory.getLogger(BaseFragment::class.java)
  protected lateinit var appKit: AppKitModel

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)

    appKit = activity?.run {
      ViewModelProviders.of(activity!!).get(AppKitModel::class.java)
    } ?: throw Exception("Invalid Activity")

    appKit.kit.observe(viewLifecycleOwner, Observer {
      appCheckup()
    })
  }

  override fun onStart() {
    super.onStart()
    appCheckup()
  }

  /**
   * Checks up the app state (wallet init, app kit is started) and navigate to appropriate page if needed.
   */
  open fun appCheckup() {
    if (!appKit.isKitReady()) {
      if (context != null && !appKit.isWalletInit(context!!)) {
        log.info("wallet has not been initialized, moving to init")
        findNavController().navigate(R.id.global_action_any_to_init_wallet)
      } else {
        log.info("appkit is not ready, moving to startup")
        findNavController().navigate(R.id.global_action_any_to_startup)
      }
    }
  }

  fun getPinDialog(callback: PinDialog.PinDialogCallback): PinDialog {
    val pinDialog = PinDialog((requireActivity() as MainActivity).getActivityThis(), android.R.style.Theme_NoTitleBar_Fullscreen, callback)
    pinDialog.setCanceledOnTouchOutside(false)
    pinDialog.setCancelable(false)
    return pinDialog
  }

}
