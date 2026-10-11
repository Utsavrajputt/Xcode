package com.invictus.kodex.github

import com.invictus.kodex.github.data.WorkflowInputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowInputParserTest {

    @Test fun emptyDispatchBraces_supportedNoInputs() {
        // The repo's own android-build.yml shape.
        val info = WorkflowInputParser.parse(
            """
            name: Android build
            on:
              push:
                branches: [ main ]
              workflow_dispatch: {}
            jobs:
              build:
                runs-on: ubuntu-latest
            """.trimIndent(),
        )
        assertTrue(info.supported)
        assertTrue(info.inputs.isEmpty())
        assertFalse(info.parseFailed)
    }

    @Test fun bareDispatchKey_supported() {
        val info = WorkflowInputParser.parse("on:\n  workflow_dispatch:\n  push:\n")
        assertTrue(info.supported)
        assertFalse(info.parseFailed)
    }

    @Test fun scalarOn() {
        assertTrue(WorkflowInputParser.parse("on: workflow_dispatch\njobs: {}").supported)
        assertFalse(WorkflowInputParser.parse("on: push\njobs: {}").supported)
    }

    @Test fun flowListOn() {
        assertTrue(WorkflowInputParser.parse("on: [push, workflow_dispatch]").supported)
        assertFalse(WorkflowInputParser.parse("on: [push, pull_request]").supported)
    }

    @Test fun listFormOn() {
        assertTrue(WorkflowInputParser.parse("on:\n  - push\n  - workflow_dispatch\n").supported)
        assertFalse(WorkflowInputParser.parse("on:\n  - push\n").supported)
    }

    @Test fun quotedOnKey() {
        assertTrue(WorkflowInputParser.parse("\"on\":\n  workflow_dispatch:\n").supported)
    }

    @Test fun noDispatchInBlock() {
        val info = WorkflowInputParser.parse("on:\n  push:\n    branches: [main]\n  pull_request:\n")
        assertFalse(info.supported)
    }

    @Test fun missingOn_failsSoftly() {
        val info = WorkflowInputParser.parse("name: broken\njobs: {}\n")
        assertTrue(info.parseFailed)
        assertTrue(info.supported) // let GitHub decide; the UI offers the raw editor
    }

    @Test fun inputsOfEveryType() {
        val info = WorkflowInputParser.parse(
            """
            on:
              workflow_dispatch:
                inputs:
                  version:
                    description: 'Version to build'   # trailing comment
                    required: true
                    default: "1.2.0"
                  debug:
                    description: Enable debug
                    type: boolean
                    default: false
                  flavor:
                    description: >
                      Which flavor
                      to build
                    type: choice
                    options:
                      - release
                      - 'debug'
                  target:
                    type: environment
                  count:
                    type: number
                    default: 3
            jobs: {}
            """.trimIndent(),
        )
        assertTrue(info.supported)
        assertFalse(info.parseFailed)
        assertEquals(listOf("version", "debug", "flavor", "target", "count"), info.inputs.map { it.name })

        val version = info.inputs[0]
        assertEquals("Version to build", version.description)
        assertTrue(version.required)
        assertEquals("string", version.type)
        assertEquals("1.2.0", version.default)

        val debug = info.inputs[1]
        assertEquals("boolean", debug.type)
        assertEquals("false", debug.default)
        assertFalse(debug.required)

        val flavor = info.inputs[2]
        assertEquals("choice", flavor.type)
        assertEquals("Which flavor to build", flavor.description)
        assertEquals(listOf("release", "debug"), flavor.options)

        assertEquals("environment", info.inputs[3].type)
        assertNull(info.inputs[3].default)
        assertEquals("3", info.inputs[4].default)
    }

    @Test fun flowAndSameIndentOptions() {
        val info = WorkflowInputParser.parse(
            """
            on:
              workflow_dispatch:
                inputs:
                  a:
                    type: choice
                    options: [x, "y z", 'w']
                  b:
                    type: choice
                    options:
                    - one
                    - two
                    default: two
            """.trimIndent(),
        )
        assertEquals(listOf("x", "y z", "w"), info.inputs[0].options)
        assertEquals(listOf("one", "two"), info.inputs[1].options)
        assertEquals("two", info.inputs[1].default)
    }

    @Test fun hashInsideQuotesIsNotAComment() {
        val info = WorkflowInputParser.parse(
            "on:\n  workflow_dispatch:\n    inputs:\n      tag:\n        description: \"build #1 only\"\n        default: 'v#2'\n",
        )
        assertEquals("build #1 only", info.inputs[0].description)
        assertEquals("v#2", info.inputs[0].default)
    }

    @Test fun inputsKeyWithoutChildren_isNotAFailure() {
        val info = WorkflowInputParser.parse("on:\n  workflow_dispatch:\n    inputs:\njobs: {}\n")
        assertTrue(info.supported)
        assertTrue(info.inputs.isEmpty())
    }

    @Test fun windowsLineEndings() {
        val info = WorkflowInputParser.parse("on:\r\n  workflow_dispatch:\r\n    inputs:\r\n      x:\r\n        required: true\r\n")
        assertEquals(1, info.inputs.size)
        assertTrue(info.inputs[0].required)
    }
}
