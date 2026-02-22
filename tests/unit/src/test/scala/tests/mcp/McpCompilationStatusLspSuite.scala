package tests.mcp

import tests.BaseLspSuite

class McpCompilationStatusLspSuite
    extends BaseLspSuite("mcp-compilation-status")
    with McpTestUtils {

  test("compilation-status-after-compile") {
    cleanWorkspace()
    for {
      _ <- initialize(
        """|/metals.json
           |{"a": {}}
           |/a/src/main/scala/com/example/Foo.scala
           |package com.example
           |
           |object Foo {
           |  def hello(): String = "hi"
           |}
           |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/com/example/Foo.scala")
      client <- startMcpServer()
      // After didOpen, BSP compilation runs → module should be up to date
      status <- client.compilationStatus()
      _ = assert(
        status.contains("up to date"),
        s"Expected 'up to date' after compilation, got: $status",
      )
    } yield ()
  }

  test("compilation-status-never-compiled") {
    cleanWorkspace()
    for {
      _ <- initialize(
        """|/metals.json
           |{"a": {}}
           |/a/src/main/scala/com/example/Bar.scala
           |package com.example
           |
           |object Bar {
           |  def greet(): String = "hello"
           |}
           |""".stripMargin
      )
      // Do NOT open or compile the file
      client <- startMcpServer()
      status <- client.compilationStatus()
      _ = assert(
        status.contains("never compiled"),
        s"Expected 'never compiled' before any compilation, got: $status",
      )
    } yield ()
  }
}
