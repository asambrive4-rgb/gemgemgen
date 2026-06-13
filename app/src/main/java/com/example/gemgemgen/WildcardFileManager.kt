package com.example.gemgemgen

interface WildcardFileManager {
    fun listFiles(): List<WildcardTextFile>
    fun readFile(file: WildcardTextFile): String
    fun createFile(fileName: String): WildcardTextFile
    fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile
    fun writeFile(file: WildcardTextFile, text: String)
    fun deleteFile(file: WildcardTextFile)
}
