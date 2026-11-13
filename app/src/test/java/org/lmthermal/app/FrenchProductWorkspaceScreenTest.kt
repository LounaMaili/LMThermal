package org.lmthermal.app

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Run the same real-screen actions with French resources and a landscape viewport. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32,36], qualifiers = "fr-w900dp-h480dp-land")
class FrenchProductWorkspaceScreenTest : ProductWorkspaceScreenTest()
