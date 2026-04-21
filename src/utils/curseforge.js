const CurseForgeAPI = {
  BASE_URL: 'https://api.curseforge.com/v1',
  API_KEY: '$2a$10$7ITQG6ypzakb2QEi0/1pheDeyxt4p7OwXDrYS7LJUJyswSKgFBizi',

  async search(query, type = 'mod', limit = 20) {
    const params = new URLSearchParams({
      gameId: '432',
      searchFilter: query,
      pageSize: String(limit),
      sortField: '2',
      sortOrder: 'desc'
    });

    if (type === 'mod') {
      params.set('modLoaderType', '4'); // Fabric
      params.set('classId', '6'); // Mods
    } else if (type === 'resourcepack') {
      params.set('classId', '12'); // Resource packs
    } else if (type === 'shader') {
      params.set('classId', '6552'); // Shader packs
    }

    const response = await fetch(`${this.BASE_URL}/mods/search?${params}`, {
      headers: {
        'x-api-key': this.API_KEY,
        'Accept': 'application/json'
      }
    });

    if (!response.ok) throw new Error(`CurseForge search failed: ${response.status}`);
    const data = await response.json();

    return (data.data || []).map(mod => ({
      id: mod.id,
      name: mod.name,
      description: mod.summary,
      author: mod.authors?.[0]?.name || 'Unknown',
      downloads: mod.downloadCount,
      icon_url: mod.logo?.thumbnailUrl || '',
      source: 'curseforge',
      project_type: type === 'resourcepack' ? 'resourcepack' : (type === 'shader' ? 'shader' : 'mod'),
      latestFiles: mod.latestFiles
    }));
  },

  getDownloadUrl(mod) {
    if (!mod.latestFiles || mod.latestFiles.length === 0) {
      return null;
    }
    const file = mod.latestFiles[0];
    return {
      url: file.downloadUrl,
      filename: file.fileName
    };
  }
};
