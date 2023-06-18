import { definePlugin } from "@runikaros/shared"

export default definePlugin({
    name: 'PluginBgmTv',
    components: {},
    extensionPoints: {
        // @see run.ikaros.api.store.enums.SubjectSyncPlatform
        // @ts-ignore
        'subject:sync:platform': 'BGM_TV'
    }
})