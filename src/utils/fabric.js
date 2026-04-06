const FabricInstaller = {
  async install(installationId, mcVersion) {
    return await window.icey.installFabric(installationId, mcVersion);
  }
};
