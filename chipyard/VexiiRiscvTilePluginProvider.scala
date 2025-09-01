package chipyard.config

import vexiiriscv.VexiiRiscvTileAttachParams
import freechips.rocketchip.subsystem.HierarchicalElementPortParamsLike

class VexiiRiscvTilePluginProvider extends TilePluginProvider {
  // VexiiRiscv tile exposes trace via core.xLen etc., but no generic trace toggle; keep trace injectors as no-ops.

  override def tilePrefetchInjectors(make: (Int, HierarchicalElementPortParamsLike) => HierarchicalElementPortParamsLike) = Seq({
    case tp: VexiiRiscvTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
      master = make(tp.tileParams.tileId, tp.crossingParams.master)))
  })
}

