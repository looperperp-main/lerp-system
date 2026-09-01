import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { Subscription, filter } from 'rxjs';

export interface BreadcrumbSegment {
  label: string;
  link?: string;
}

@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [RouterLink],
  template: `
    <span class="eyebrow">
      <i class="dot"></i>
      @for (seg of segments(); track seg.label; let last = $last) {
        @if (seg.link && !last) {
          <a [routerLink]="seg.link" class="crumb-link">{{ seg.label }}</a>
        } @else {
          <span>{{ seg.label }}</span>
        }
        @if (!last) {
          <span class="crumb-sep">/</span>
        }
      }
    </span>
  `,
})
export class Breadcrumb implements OnInit, OnDestroy {
  segments = signal<BreadcrumbSegment[]>([{ label: 'Overview' }]);
  private sub?: Subscription;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.update();
    this.sub = this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.update());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private update(): void {
    let r = this.route.root;
    while (r.firstChild) r = r.firstChild;
    const breadcrumb = r.snapshot.data['breadcrumb'] as BreadcrumbSegment[] | undefined;
    this.segments.set(breadcrumb ?? [{ label: 'Overview' }]);
  }
}
