import React from 'react'

export function CollectionPlugin() {
  return (
    <div className="p-6 max-w-2xl">
      <h1 className="text-2xl font-bold mb-2">The Public Records Collection</h1>
      <p className="text-muted-foreground mb-4">
        This page is served from the The Public Records Collection&rsquo;s user UI content directory.
      </p>
      <div className="rounded-lg border p-4 text-sm text-muted-foreground">
        Installed Elements can inject custom user-facing UI by placing a plugin.json
        and plugin.bundle.js in their ui/user/ content directory.
      </div>
    </div>
  )
}
