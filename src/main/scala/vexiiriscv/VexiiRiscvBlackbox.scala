//******************************************************************************
// Copyright (c) 2024, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// VexiiRiscv Tile Wrapper
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package vexiiriscv

import sys.process._

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam, RawParam}

import scala.collection.mutable.{ListBuffer}
import java.io.File

case class VexiiRiscvAddressParams(
  address: BigInt,
  size: BigInt,
  main: Boolean) {
  val mainStr = if (main) "1" else "0"
  def str = f"--region base=$address%X,size=$size%X,main=$mainStr,exe=1"
}


class VexiiRiscvTilelink(regions: Seq[VexiiRiscvAddressParams]) extends BlackBox with HasBlackBoxPath {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val reset = Input(Bool())

    val hartId = Input(UInt(32.W))

    val mti_flag = Input(Bool())
    val msi_flag = Input(Bool())
    val mei_flag = Input(Bool())
    val sei_flag = Input(Bool())

    val mem_node_bus = new Bundle {
      val a = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val payload = Output(new Bundle {
          val opcode = UInt(3.W)
          val param = UInt(3.W)
          val source = UInt(4.W)
          val address = UInt(32.W)
          val size = UInt(3.W)
          val mask = UInt(8.W)
          val data = UInt(64.W)
          val corrupt = Bool()
        })
      }
      val b = new Bundle {
        val valid = Input(Bool())
        val ready = Output(Bool())
        val payload = Input(new Bundle {
          val opcode = UInt(3.W)
          val param = UInt(3.W)
          val source = UInt(4.W)
          val address = UInt(32.W)
          val size = UInt(3.W)
        })
      }
      val c = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val payload = Output(new Bundle {
          val opcode = UInt(3.W)
          val param = UInt(3.W)
          val source = UInt(4.W)
          val address = UInt(32.W)
          val size = UInt(3.W)
          val data = UInt(64.W)
          val corrupt = Bool()
        })
      }
      val d = new Bundle {
        val valid = Input(Bool())
        val ready = Output(Bool())
        val payload = Input(new Bundle {
          val opcode = UInt(3.W)
          val param = UInt(3.W)
          val source = UInt(4.W)
          val size = UInt(3.W)
          val data = UInt(64.W)
          val corrupt = Bool()
          val denied = Bool()
          val sink = UInt(4.W)
        })
      }
      val e = new Bundle {
        val valid = Output(Bool())
        val ready = Input(Bool())
        val payload = Output(new Bundle {
          val sink = UInt(4.W)
        })
      }
    }
  })

  val chipyardDir = System.getProperty("user.dir")
  val vexiiRiscvDir = s"$chipyardDir/generators/vexiiriscv/VexiiRiscv"

  val args = (Seq(
    "Test/runMain",
    "vexiiriscv.GenerateTilelink",
    "--with-fetch-l1",
    "--with-lsu-l1",
    "--lsu-l1-coherency",
    "--fetch-l1-hardware-prefetch=nl",
    "--fetch-l1-refill-count=2",
    "--lsu-software-prefetch",
    "--lsu-hardware-prefetch rpt",
    "--performance-counters 9",
    "--regfile-async",
    "--lsu-l1-store-buffer-ops=32",
    "--lsu-l1-refill-count 4",
    "--lsu-l1-writeback-count 4",
    "--lsu-l1-store-buffer-slots=4",
    "--with-mul",
    "--with-div",
    "--allow-bypass-from=0",
    "--with-lsu-bypass",
    "--with-rva",
    "--with-supervisor",
    "--fetch-l1-ways=4",
    "--fetch-l1-mem-data-width-min=128",
    "--lsu-l1-ways=4",
    "--lsu-l1-mem-data-width-min=128",
    "--xlen=64",
    "--with-rvc",
    "--with-rvf",
    "--with-rvd",
    "--with-btb",
    "--with-ras",
    "--with-gshare",
    "--with-late-alu",
    "--decoders=2",
    "--lanes=2",
    "--with-dispatcher-buffer",
    "--with-hart-id-input",
    "--reset-vector=0x10000",
    "--with-whiteboxer-outputs",
    "--with-boot-mem-init",
    "--tl-sink-width=4"
  ) ++ regions.map(_.str)).mkString(" ")
  println(s"Running VexiiRiscv generator with args: $args")
  val proc = Process(s"sbt \"$args\"", new File(vexiiRiscvDir)).!!

  addPath(s"$vexiiRiscvDir/VexiiRiscvTilelink.v")
}
