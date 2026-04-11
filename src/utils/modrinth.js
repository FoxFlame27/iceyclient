const ModrinthAPI = {
  BASE_URL: 'https://api.modrinth.com/v2',

  async search(query, type = 'mod', limit = 20, offset = 0) {
    let facets;
    if (type === 'mod') {
      facets = JSON.stringify([['project_type:mod'], ['categories:fabric']]);
    } else if (type === 'resourcepack') {
      facets = JSON.stringify([['project_type:resourcepack']]);
    } else {
      facets = JSON.stringify([['categories:fabric']]);
    }

    const params = new URLSearchParams({
      query: query,
      facets: facets,
      limit: String(limit),
      offset: String(offset)
    });

    const response = await fetch(`${this.BASE_URL}/search?${params}`, {
      headers: { 'User-Agent': 'IceyClient/1.0.0' }
    });

    if (!response.ok) throw new Error(`Modrinth search failed: ${response.status}`);
    const data = await response.json();

    return data.hits.map(hit => ({
      id: hit.project_id,
      name: hit.title,
      description: hit.description,
      author: hit.author,
      downloads: hit.downloads,
      icon_url: hit.icon_url,
      source: 'modrinth',
      project_type: hit.project_type,
      slug: hit.slug
    }));
  },

  async getVersions(projectId, mcVersion) {
    const params = new URLSearchParams();
    if (mcVersion) {
      params.set('game_versions', JSON.stringify([mcVersion]));
    }

    const response = await fetch(`${this.BASE_URL}/project/${projectId}/version?${params}`, {
      headers: { 'User-Agent': 'IceyClient/1.0.0' }
    });

    if (!response.ok) throw new Error(`Modrinth versions failed: ${response.status}`);
    return response.json();
  },

  async getAllVersions(projectId) {
    const response = await fetch(`${this.BASE_URL}/project/${projectId}/version`, {
      headers: { 'User-Agent': 'IceyClient/1.0.0' }
    });
    if (!response.ok) throw new Error(`Modrinth versions failed: ${response.status}`);
    return response.json();
  },

  async getDownloadUrl(projectId, mcVersion, loader = 'fabric') {
    // Strict: only return a version that matches the MC version AND loader
    const params = new URLSearchParams();
    params.set('game_versions', JSON.stringify([mcVersion]));
    params.set('loaders', JSON.stringify([loader]));

    const response = await fetch(`${this.BASE_URL}/project/${projectId}/version?${params}`, {
      headers: { 'User-Agent': 'IceyClient/1.0.0' }
    });
    if (!response.ok) throw new Error(`Modrinth versions failed: ${response.status}`);
    const versions = await response.json();

    if (versions.length === 0) {
      throw new Error(`No ${loader} version found for MC ${mcVersion}`);
    }
    const file = versions[0].files.find(f => f.primary) || versions[0].files[0];
    return { url: file.url, filename: file.filename };
  },

  // Get list of MC versions this project supports (deduped)
  async getSupportedMcVersions(projectId) {
    const all = await this.getAllVersions(projectId);
    const versions = new Set();
    for (const v of all) {
      for (const gv of (v.game_versions || [])) {
        versions.add(gv);
      }
    }
    // Sort newest first using natural version sort
    return [...versions].sort((a, b) => {
      const pa = a.split('.').map(n => parseInt(n) || 0);
      const pb = b.split('.').map(n => parseInt(n) || 0);
      for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
        const da = pa[i] || 0;
        const db = pb[i] || 0;
        if (da !== db) return db - da;
      }
      return 0;
    });
  },

  // Get supported loaders for this project
  async getSupportedLoaders(projectId) {
    const all = await this.getAllVersions(projectId);
    const loaders = new Set();
    for (const v of all) {
      for (const l of (v.loaders || [])) {
        loaders.add(l);
      }
    }
    return [...loaders];
  }
};
