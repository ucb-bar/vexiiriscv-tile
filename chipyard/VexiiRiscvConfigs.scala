package chipyard

import chisel3._

import org.chipsalliance.cde.config.Config

// VexiiRiscv Configs moved under the vexiiriscv generator for modularity

class VexiiRiscvConfig extends Config(
  new vexiiriscv.WithNVexiiRiscvCores(1) ++
  new chipyard.config.AbstractConfig)

