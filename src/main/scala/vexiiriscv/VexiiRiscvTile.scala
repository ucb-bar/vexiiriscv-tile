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

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, StringParam, RawParam}

import scala.collection.mutable.{ListBuffer}

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._

case class VexiiRiscvCoreParams() extends CoreParams {
  val fetchWidth: Int = 2
  val nL2TLBEntries = 0
  val nL2TLBWays = 0
  val retireWidth = fetchWidth
  override def minFLen: Int = 32
  val xLen = 64
  val pgLevels = 3
  val bootFreqHz: BigInt = 0
  val decodeWidth: Int = fetchWidth
  val fpu: Option[freechips.rocketchip.tile.FPUParams] = Some(FPUParams(minFLen = 32,
    sfmaLatency=4, dfmaLatency=4, divSqrt=true))
  val haveBasicCounters: Boolean = true
  val haveCFlush: Boolean = false
  val haveFSDirty: Boolean = true
  val instBits: Int = 16
  def lrscCycles: Int = 30
  val mcontextWidth: Int = 0
  val misaWritable: Boolean = false
  val mtvecInit: Option[BigInt] = Some(BigInt(0))
  val mtvecWritable: Boolean = true
  val mulDiv: Option[freechips.rocketchip.rocket.MulDivParams] = Some(MulDivParams(mulUnroll=0, divEarlyOut=true))
  val nBreakpoints: Int = 0
  val nLocalInterrupts: Int = 0
  val nPMPs: Int = 0
  val nPerfCounters: Int = 0
  val pmpGranularity: Int = 4
  val scontextWidth: Int = 0
  val useAtomics: Boolean = true
  val useAtomicsOnlyForIO: Boolean = false
  val useBPWatch: Boolean = false
  val useCompressed: Boolean = true
  val useDebug: Boolean = true
  val useNMI: Boolean = false
  val useRVE: Boolean = false
  val useSCIE: Boolean = false
  val useSupervisor: Boolean = false
  val useUser: Boolean = false
  val useVM: Boolean = true
  val nPTECacheEntries: Int = 0
  val useHypervisor: Boolean = false
  val useConditionalZero = false
  val useZba = false
  val useZbb = false
  val useZbs = false
  override val useVector = false
  override def vLen = 0
  override def eLen = 64
  override def vfLen = 0
  override def vfh = false
  override def vExts = Nil
  val traceHasWdata: Boolean = false
}

case class VexiiRiscvTileAttachParams(
  tileParams: VexiiRiscvTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile {
  type TileType = VexiiRiscvTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
}

case class VexiiRiscvTileParams(
  name: Option[String] = Some("vexiiriscv_tile"),
  tileId: Int = 0,
  val core: VexiiRiscvCoreParams = VexiiRiscvCoreParams()
) extends InstantiableTileParams[VexiiRiscvTile]
{
  val beuAddr: Option[BigInt] = None
  val blockerCtrlAddr: Option[BigInt] = None
  val btb: Option[BTBParams] = None
  val boundaryBuffers: Boolean = false
  val dcache: Option[DCacheParams] = None
  val icache: Option[ICacheParams] = None
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
  def instantiate(crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): VexiiRiscvTile = {
    new VexiiRiscvTile(this, crossing, lookup)
  }
  val baseName = name.getOrElse("vexiiriscv_tile")
  val uniqueName = s"${baseName}_$tileId"
}

class VexiiRiscvTile private(
  val vexiiriscvParams: VexiiRiscvTileParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters)
  extends BaseTile(vexiiriscvParams, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{

  def this(params: VexiiRiscvTileParams, crossing: HierarchicalElementCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  // TileLink nodes
  val intOutwardNode = None
  val masterNode = visibilityNode
  val slaveNode = TLIdentityNode()

  tlOtherMastersNode := tlMasterXbar.node
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  override lazy val module = new VexiiRiscvTileModuleImp(this)

  val portName = "vexiiriscv-mem-port"
  val node = TLIdentityNode()

  val dmemNode = TLClientNode(
    Seq(TLMasterPortParameters.v1(
      clients = Seq(TLMasterParameters.v1(
        name = portName,
        sourceId = IdRange(0, 1))))))

  val imemNode = TLClientNode(
    Seq(TLMasterPortParameters.v1(
      clients = Seq(TLMasterParameters.v1(
        name = portName,
        sourceId = IdRange(0, 1))))))

  tlMasterXbar.node := node := TLBuffer() := dmemNode
  tlMasterXbar.node := node := TLBuffer() := imemNode

  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("spinalhdl,vexiiriscv", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++
                        cpuProperties ++
                        nextLevelCacheProperty ++
                        tileProperties)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(tileId))
  }

  def connectVexiiRiscvInterrupts(debug: Bool, msip: Bool, mtip: Bool, meip: Bool) = {
    val (interrupts, _) = intSinkNode.in(0)
    debug := interrupts(0)
    msip := interrupts(1)
    mtip := interrupts(2)
    meip := interrupts(3)
  }
}

class VexiiRiscvTileModuleImp(outer: VexiiRiscvTile) extends BaseTileModuleImp(outer){
}
