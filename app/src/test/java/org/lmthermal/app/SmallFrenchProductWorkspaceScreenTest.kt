package org.lmthermal.app

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** French, narrow portrait and 200% text: actual actions must remain reachable without moving the stage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 36], qualifiers = "fr-w320dp-h568dp-port")
class SmallFrenchProductWorkspaceScreenTest : ProductWorkspaceScreenTest() {
    override val viewport = 320 to 568
    override val fontScale = 2f
}
