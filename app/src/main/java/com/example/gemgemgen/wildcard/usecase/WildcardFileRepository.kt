// 역할: 와일드카드 파일 읽기 및 쓰기 작업을 위한 저장소 인터페이스를 정의합니다.
package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.wildcard.domain.WildcardTextFile

interface WildcardFileRepository {
    fun listFiles(): List<WildcardTextFile>
    fun readFile(file: WildcardTextFile): String
    fun createFile(fileName: String): WildcardTextFile
    fun renameFile(file: WildcardTextFile, newName: String): WildcardTextFile
    fun writeFile(file: WildcardTextFile, text: String)
    fun deleteFile(file: WildcardTextFile)
}

