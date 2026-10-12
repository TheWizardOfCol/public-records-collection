import { CollectionPlugin } from './CollectionPlugin'

declare const window: Window & {
  __elementsPlugins?: {
    register(route: string, component: unknown): void
  }
}

window.__elementsPlugins?.register('collection', CollectionPlugin)
