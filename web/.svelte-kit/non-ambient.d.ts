
// this file is generated — do not edit it


declare module "svelte/elements" {
	export interface HTMLAttributes<T> {
		'data-sveltekit-keepfocus'?: true | '' | 'off' | undefined | null;
		'data-sveltekit-noscroll'?: true | '' | 'off' | undefined | null;
		'data-sveltekit-preload-code'?:
			| true
			| ''
			| 'eager'
			| 'viewport'
			| 'hover'
			| 'tap'
			| 'off'
			| undefined
			| null;
		'data-sveltekit-preload-data'?: true | '' | 'hover' | 'tap' | 'off' | undefined | null;
		'data-sveltekit-reload'?: true | '' | 'off' | undefined | null;
		'data-sveltekit-replacestate'?: true | '' | 'off' | undefined | null;
	}
}

export {};


declare module "$app/types" {
	type MatcherParam<M> = M extends (param : string) => param is (infer U extends string) ? U : string;

	export interface AppTypes {
		RouteId(): "/" | "/about" | "/admin" | "/auth" | "/channel" | "/channel/[name]" | "/chat" | "/chat/[username]" | "/forgot" | "/messages" | "/notifications" | "/reset" | "/settings" | "/upload" | "/verify" | "/watch" | "/watch/[id]" | "/wheels";
		RouteParams(): {
			"/channel/[name]": { name: string };
			"/chat/[username]": { username: string };
			"/watch/[id]": { id: string }
		};
		LayoutParams(): {
			"/": { name?: string | undefined; username?: string | undefined; id?: string | undefined };
			"/about": Record<string, never>;
			"/admin": Record<string, never>;
			"/auth": Record<string, never>;
			"/channel": { name?: string | undefined };
			"/channel/[name]": { name: string };
			"/chat": { username?: string | undefined };
			"/chat/[username]": { username: string };
			"/forgot": Record<string, never>;
			"/messages": Record<string, never>;
			"/notifications": Record<string, never>;
			"/reset": Record<string, never>;
			"/settings": Record<string, never>;
			"/upload": Record<string, never>;
			"/verify": Record<string, never>;
			"/watch": { id?: string | undefined };
			"/watch/[id]": { id: string };
			"/wheels": Record<string, never>
		};
		Pathname(): "/" | "/about" | "/admin" | "/auth" | "/channel" | `/channel/${string}` & {} | `/chat/${string}` & {} | "/forgot" | "/messages" | "/notifications" | "/reset" | "/settings" | "/upload" | "/verify" | "/watch" | `/watch/${string}` & {} | "/wheels";
		ResolvedPathname(): `${"" | `/${string}`}${ReturnType<AppTypes['Pathname']>}`;
		Asset(): "/favicon.png" | "/watchshark.webp" | string & {};
	}
}