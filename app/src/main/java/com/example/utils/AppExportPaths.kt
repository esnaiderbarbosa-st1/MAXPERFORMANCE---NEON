package com.example.utils

import android.os.Environment

/**
 * Constantes unificadas de diretórios públicos usados para exportar imagens, vídeos,
 * PDFs e planilhas CSV do aplicativo no armazenamento compartilhado do Android.
 */
object AppExportPaths {
    const val PUBLIC_FOLDER_NAME = "MAXPERFORMANCE"

    val PICTURES_RELATIVE_PATH: String = "${Environment.DIRECTORY_PICTURES}/$PUBLIC_FOLDER_NAME"
    val PICTURES_CAPTURES_RELATIVE_PATH: String = "${Environment.DIRECTORY_PICTURES}/$PUBLIC_FOLDER_NAME/Capturas"
    val DOWNLOADS_RELATIVE_PATH: String = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_FOLDER_NAME"
    val DOWNLOADS_CSV_RELATIVE_PATH: String = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_FOLDER_NAME/CSV"
    val MOVIES_RELATIVE_PATH: String = "${Environment.DIRECTORY_MOVIES}/$PUBLIC_FOLDER_NAME"
}
