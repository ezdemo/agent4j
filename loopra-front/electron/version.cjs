function compareVersions(a, b) {
  const pa = String(a || '').replace(/^v/i, '').split('.').map((part) => Number.parseInt(part, 10) || 0)
  const pb = String(b || '').replace(/^v/i, '').split('.').map((part) => Number.parseInt(part, 10) || 0)

  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const na = pa[i] || 0
    const nb = pb[i] || 0
    if (na > nb) return 1
    if (na < nb) return -1
  }
  return 0
}

module.exports = { compareVersions }
