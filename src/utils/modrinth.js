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
    params.set('loaders', JSON.stringify(['fabric']));

    const response = await fetch(`${this.BASE_URL}/project/${projectId}/version?${params}`, {
      headers: { 'User-Agent': 'IceyClient/1.0.0' }
    });

    if (!response.ok) throw new Error(`Modrinth versions failed: ${response.status}`);
    return response.json();
  },

  async getDownloadUrl(projectId, mcVersion) {
    const versions = await this.getVersions(projectId, mcVersion);
    if (versions.length === 0) {
      // Try without version filter
      const allVersions = await this.getVersions(projectId);
      if (allVersions.length === 0) throw new Error('No versions found');
      const file = allVersions[0].files.find(f => f.primary) || allVersions[0].files[0];
      return { url: file.url, filename: file.filename };
    }
    const file = versions[0].files.find(f => f.primary) || versions[0].files[0];
    return { url: file.url, filename: file.filename };
  }
};
