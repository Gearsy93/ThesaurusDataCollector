package com.gearsy.vinitirubricatorparser

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<VinitiRubnricatorParserApplication>().with(TestcontainersConfiguration::class).run(*args)
}
