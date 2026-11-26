package fr.gotraceo.live.widget

import android.net.ConnectivityManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundDataTest {

    @Test
    fun `restriction active bloque les widgets`() {
        assertTrue(WidgetUpdater.blocksBackgroundData(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED))
    }

    @Test
    fun `sans restriction ou appli exemptee, rien n'est bloque`() {
        assertFalse(WidgetUpdater.blocksBackgroundData(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED))
        assertFalse(WidgetUpdater.blocksBackgroundData(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED))
    }
}
