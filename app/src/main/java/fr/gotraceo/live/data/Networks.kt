package fr.gotraceo.live.data

// Réseaux de transport que l'application sait afficher.
// Ce fichier n'utilise aucune API Android afin d'être testable sur JVM.

object Networks {

    /**
     * L'Île-de-France, réseau d'origine de l'application. C'est aussi le réseau de tout ce qui a été
     * enregistré avant l'arrivée des autres : une configuration sans réseau est francilienne.
     */
    const val IDFM = "idfm"

    fun isIdfm(networkId: String): Boolean = networkId == IDFM
}
