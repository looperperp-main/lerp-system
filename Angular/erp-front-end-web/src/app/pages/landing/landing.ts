import { Component, AfterViewInit, OnDestroy, ElementRef } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing implements AfterViewInit, OnDestroy {
  private observer!: IntersectionObserver;
  private scrollHandler!: () => void;

  constructor(private router: Router, private el: ElementRef) {}

  ngAfterViewInit(): void {
    this.setupScrollNav();
    this.setupReveal();
  }

  ngOnDestroy(): void {
    window.removeEventListener('scroll', this.scrollHandler);
    this.observer?.disconnect();
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }

  goToPartnerSignup() {
    this.router.navigate(['/cadastrar-parceiro']);
  }

  scrollTo(id: string) {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
  }

  private setupScrollNav() {
    const nav = document.getElementById('nav');
    this.scrollHandler = () => nav?.classList.toggle('scrolled', window.scrollY > 8);
    window.addEventListener('scroll', this.scrollHandler, { passive: true });
    this.scrollHandler();
  }

  private setupReveal() {
    this.observer = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          e.target.classList.add('in');
          this.observer.unobserve(e.target);
        }
      });
    }, { threshold: 0, rootMargin: '0px 0px -40px 0px' });

    this.el.nativeElement.querySelectorAll('.reveal').forEach((el: Element) => {
      this.observer.observe(el);
    });
  }
}