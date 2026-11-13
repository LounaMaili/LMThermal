package org.lmthermal.app

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Short landscape with large text qualifies the real error/unavailable/measurement controls on the host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 36], qualifiers = "en-w640dp-h320dp-land")
class SmallLandscapeProductWorkspaceScreenTest : ProductWorkspaceScreenTest() {
    override val viewport = 640 to 320
    override val fontScale = 2f
}
